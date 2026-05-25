package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
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

    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    IngestionService ingestionService;

    @Test
    void 새로운_스트림과_종료된_스트림이_있을때_StreamChangedEvent를_한번만_발행해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 123L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> newStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(streamTarget1), Set.of("ch2"), Set.of());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(newStreams);
        when(streamRepository.update(newStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        ArgumentCaptor<StreamChangedEvent> captor = ArgumentCaptor.forClass(StreamChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        StreamChangedEvent event = captor.getValue();
        assertThat(event.newStreamIds()).containsExactly(streamTarget1);
        assertThat(event.closedStreamIds()).containsExactly("ch2");
        // 동기화 호출 확인도 추가하면 좋습니다.
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "TALK", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(eventPublisher, never()).publishEvent(any());
        verify(apiServerClient).syncStreams(anyList()); // 상태 변화 없어도 동기화는 호출됨
    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {
        // given
        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenThrow(new RuntimeException("API Error"));

        // when & then
        assertThrows(RuntimeException.class, () -> ingestionService.ingest());
    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {
        // given
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 2L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1, streamTarget2);

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(streamRepository.update(liveStreams)).thenReturn(new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet()));

        // when
        ingestionService.ingest();

        // then
        verify(streamRepository).update(liveStreams);
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 방송_상태_변화가_없더라도_API_서버_동기화는_항상_호출되어야_한다() {
        // given
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(streamRepository.update(targets)).thenReturn(new StreamUpdateResults(Set.of(), Set.of(), Set.of()));

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient, times(1)).syncStreams(anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 방송_상태_변화가_있으면_동기화와_이벤트_발행_둘_다_수행한다() {
        // given
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(streamRepository.update(targets)).thenReturn(new StreamUpdateResults(Set.of(target), Set.of(), Set.of()));

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient).syncStreams(anyList());
        verify(eventPublisher).publishEvent(any(StreamChangedEvent.class));
    }

    @Test
    void 메타데이터_변경이_감지되면_API_서버에_세그먼트_기록을_전송해야_한다() {
        // given
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        ChangedStream changed = new ChangedStream("ch1", "롤", "롤 솔랭", "GAME", "GAME");
        Set<ChangedStream> changedStreams = Set.of(changed);

        StreamUpdateResults results = new StreamUpdateResults(
            Set.of(),
            Set.of(),
            changedStreams
        );

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(streamRepository.update(anyList())).thenReturn(results);

        // when
        ingestionService.ingest();

        // then
        verify(apiServerClient).recordNewSegments(anyList());
    }

    @Test
    void 메타데이터_변경이_없으면_API_서버에_세그먼트_기록을_전송하지_않아야_한다() {
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of()); // changedStreams 비어있음

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(streamRepository.update(anyList())).thenReturn(results);

        ingestionService.ingest();

        verify(apiServerClient, never()).recordNewSegments(anyList());
    }
}
