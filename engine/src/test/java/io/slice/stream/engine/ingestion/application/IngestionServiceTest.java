package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.service.StreamUpdateAnalyzer;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class IngestionServiceTest {

    @Mock
    StreamDiscoveryClient discoveryClient;

    @Mock
    StreamRepository streamRepository;

    @Mock
    ApiServerClient apiServerClient;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    StreamUpdateAnalyzer streamUpdateAnalyzer;

    @InjectMocks
    IngestionService ingestionService;

    @Test
    void 새로운_스트림과_종료된_스트림이_있을때_StreamChangedEvent를_한번만_발행해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 123L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 124L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> newStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(streamTarget1), Set.of(streamTarget2), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(newStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1", "ch2"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        ArgumentCaptor<StreamChangedEvent> captor = ArgumentCaptor.forClass(StreamChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        StreamChangedEvent event = captor.getValue();
        assertThat(event.newStreams()).containsExactly(streamTarget1);
        assertThat(event.closedStreams()).containsExactly(streamTarget2);
        verify(apiServerClient).syncStreams(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), newStreams);
    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "TALK", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(streamTarget1));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(apiServerClient).syncStreams(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), liveStreams);
    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {
        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenThrow(new RuntimeException("API Error"));

        assertThrows(RuntimeException.class, () -> ingestionService.ingest());
    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 2L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1, streamTarget2);
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1", "ch2"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(streamRepository).sync(results.closedStreamIds(), liveStreams);
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 방송_상태_변화가_없더라도_API_서버_동기화는_항상_호출되어야_한다() {
        // given
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(target));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient, times(1)).syncStreams(anyList());
        verify(eventPublisher, never()).publishEvent(any());
        verify(streamRepository).sync(results.closedStreamIds(), targets);
    }

    @Test
    void 방송_상태_변화가_있으면_동기화와_이벤트_발행_둘_다_수행한다() {
        // given
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(target), Set.of(), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of());
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient).syncStreams(anyList());
        verify(eventPublisher).publishEvent(any(StreamChangedEvent.class));
        verify(streamRepository).sync(results.closedStreamIds(), targets);
    }

    @Test
    void 메타데이터_변경이_감지되면_API_서버에_세그먼트_기록을_전송해야_한다() {
        // given
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        ChangedStream changed = new ChangedStream("ch1", "롤", "롤 솔랭", "GAME", "GAME", Instant.EPOCH, 0L);
        Set<ChangedStream> changedStreams = Set.of(changed);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), changedStreams);

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(dummyTarget));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient).recordNewSegments(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), List.of(dummyTarget));
    }

    @Test
    void 메타데이터_변경이_없으면_API_서버에_세그먼트_기록을_전송하지_않아야_한다() {
        // given
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(dummyTarget));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient, never()).recordNewSegments(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), List.of(dummyTarget));
    }

    @Test
    void 탑스트림_목록에_없지만_기존에_추적중인_방송은_순위밖_조회를_수행하여_유지해야_한다() {
        // given
        // topLives에는 ch1만 존재
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 100, "url", "GAME", Instant.EPOCH);
        List<StreamTarget> topLiveStreams = List.of(streamTarget1);
        
        // activeChannelIds에는 ch1과 ch2(순위 밖으로 밀려남)가 존재
        Set<String> activeChannelIds = Set.of("ch1", "ch2");
        
        // 순위 밖 API 조회를 통해 ch2가 아직 살아있음을 확인했다고 가정
        StreamTarget rankoutTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 2L, "title2", 50, "url", "GAME", Instant.EPOCH);
        List<StreamTarget> rankoutStreams = List.of(rankoutTarget2);

        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(topLiveStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(activeChannelIds);
        
        // 여집합 조회 모킹 (ch2에 대해 순위 밖 조회가 발생해야 함)
        when(discoveryClient.fetchLiveStreams(Set.of("ch2"))).thenReturn(rankoutStreams);
        
        // getStreamTargets는 currentTargets (ch1, ch2) 리스트를 받아 처리함
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(streamTarget1, rankoutTarget2));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        // 1. fetchLiveStreams가 정확히 ch2(여집합)로 호출되었는지 검증
        verify(discoveryClient).fetchLiveStreams(Set.of("ch2"));
        
        // 2. ch1(topLive) + ch2(rankout) 합쳐져서 저장소 동기화가 수행되었는지 검증
        verify(streamRepository).sync(results.closedStreamIds(), List.of(streamTarget1, rankoutTarget2));
    }
}
