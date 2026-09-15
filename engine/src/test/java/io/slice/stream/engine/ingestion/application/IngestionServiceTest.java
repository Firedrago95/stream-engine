package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.domain.service.StreamUpdateAnalyzer;
import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
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

    @Mock
    TargetStreamPool targetStreamPool;

    @InjectMocks
    IngestionService ingestionService;

    @Test
    void 새로운_스트림과_종료된_스트림이_있을때_StreamChangedEvent를_한번만_발행해야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 123L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 124L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> topLiveStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(streamTarget1), Set.of(streamTarget2), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(topLiveStreams);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(streamTarget1));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1", "ch2"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        ArgumentCaptor<StreamChangedEvent> captor = ArgumentCaptor.forClass(StreamChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        StreamChangedEvent event = captor.getValue();
        assertThat(event.newStreams()).containsExactly(streamTarget1);
        assertThat(event.closedStreams()).containsExactly(streamTarget2);
        verify(apiServerClient).syncStreams(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), List.of(streamTarget1));
    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "TALK", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1);
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(liveStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(streamTarget1));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(eventPublisher, never()).publishEvent(any());
        verify(apiServerClient).syncStreams(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), liveStreams);
    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {
        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenThrow(new RuntimeException("API Error"));

        assertDoesNotThrow(() -> ingestionService.ingest());
        verify(apiServerClient, never()).syncStreams(anyList());
        verify(streamRepository, never()).sync(any(), any());
    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 2L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1, streamTarget2);
        StreamUpdateResults results = new StreamUpdateResults(Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(liveStreams);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1", "ch2"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1", "ch2"))).thenReturn(liveStreams);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1", "ch2"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(streamRepository).sync(results.closedStreamIds(), liveStreams);
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 방송_상태_변화가_없더라도_API_서버_동기화는_항상_호출되어야_한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(targets);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(target));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(apiServerClient, times(1)).syncStreams(anyList());
        verify(eventPublisher, never()).publishEvent(any());
        verify(streamRepository).sync(results.closedStreamIds(), targets);
    }

    @Test
    void 방송_상태_변화가_있으면_동기화와_이벤트_발행_둘_다_수행한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        List<StreamTarget> targets = List.of(target);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(target), Set.of(), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(targets);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(targets);
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of());
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(apiServerClient).syncStreams(anyList());
        verify(eventPublisher).publishEvent(any(StreamChangedEvent.class));
        verify(streamRepository).sync(results.closedStreamIds(), targets);
    }

    @Test
    void 메타데이터_변경이_감지되면_API_서버에_세그먼트_기록을_전송해야_한다() {
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        ChangedStream changed = new ChangedStream("ch1", "live1", "롤", "롤 솔랭", "GAME", "GAME", Instant.EPOCH, 0L);
        Set<ChangedStream> changedStreams = Set.of(changed);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), changedStreams, Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(dummyTarget));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(dummyTarget));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(apiServerClient).recordNewSegments(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), List.of(dummyTarget));
    }

    @Test
    void 메타데이터_변경이_없으면_API_서버에_세그먼트_기록을_전송하지_않아야_한다() {
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(dummyTarget));
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(dummyTarget));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of(dummyTarget));
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(apiServerClient, never()).recordNewSegments(anyList());
        verify(streamRepository).sync(results.closedStreamIds(), List.of(dummyTarget));
    }

    @Test
    void 전체_방송_중_타겟_명단에_포함된_채널만_상세_조회하여_저장소에_동기화한다() {
        StreamTarget targetStream = new StreamTarget("target1", "타겟스트리머", null, 1L, "타겟제목", 100, "url1", "GAME", null);
        StreamTarget nonTargetStream = new StreamTarget("normal1", "일반스트리머", null, 2L, "일반제목", 10, "url2", "TALK", null);
        List<StreamTarget> allLiveStreams = List.of(targetStream, nonTargetStream);

        StreamTarget detailedTarget = new StreamTarget("target1", "타겟스트리머", "chat1", 1L, "타겟제목", 100, "url1", "GAME", Instant.EPOCH);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(detailedTarget), Set.of(), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(allLiveStreams);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("target1"));
        when(discoveryClient.fetchLiveStreams(Set.of("target1"))).thenReturn(List.of(detailedTarget));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of());
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(discoveryClient).fetchLiveStreams(Set.of("target1"));
        verify(discoveryClient, never()).fetchLiveStreams(Set.of("normal1"));
        verify(streamRepository).sync(results.closedStreamIds(), List.of(detailedTarget));
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 타겟_명단에_포함된_방송이_없으면_상세_조회를_호출하지_않아야_한다() {
        StreamTarget nonTargetStream = new StreamTarget("normal1", "일반스트리머", null, 2L, "일반제목", 10, "url2", "TALK", null);
        List<StreamTarget> allLiveStreams = List.of(nonTargetStream);
        StreamUpdateResults results = new StreamUpdateResults(Set.of(), Set.of(), Set.of(), Instant.now());

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(allLiveStreams);
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("target1"));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of());
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class))).thenReturn(results);

        ingestionService.ingest();

        verify(discoveryClient, never()).fetchLiveStreams(anySet());
        verify(streamRepository).sync(results.closedStreamIds(), Collections.emptyList());
        verify(apiServerClient).syncStreams(anyList());
    }

    @Test
    void 과거_활성_채널_목록을_기반으로_이전_스트림_상세_정보를_조회해야_한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 10, "url", "GAME", Instant.EPOCH);
        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(target));
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(target));
        when(streamRepository.getActiveChannelIds()).thenReturn(Set.of("ch1", "ch_closed"));
        when(streamRepository.getStreamTargets(anyList())).thenReturn(List.of());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class)))
            .thenReturn(new StreamUpdateResults(Set.of(), Set.of(), Set.of(), Instant.now()));

        ingestionService.ingest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(streamRepository).getStreamTargets(captor.capture());

        assertThat(captor.getValue()).containsExactlyInAnyOrder("ch1", "ch_closed");
    }

    @Test
    void 타겟_채널에_대해서는_상세_조회된_실시간_시청자수와_메타데이터로_병합하여_API_서버에_동기화한다() {
        Instant detailedStartedAt = Instant.parse("2026-09-15T10:00:00Z");
        StreamTarget targetCached = new StreamTarget("ch1", "침착맨", null, 1001L, "침착맨 방송", 1000, "url1", "소통", null);
        StreamTarget nonTarget = new StreamTarget("ch2", "비타겟", null, 1002L, "일반 방송", 500, "url2", "게임", null);
        StreamTarget targetDetailed = new StreamTarget("ch1", "침착맨", "chatCh1", 1001L, "침착맨 방송", 3500, "url1", "소통", detailedStartedAt);

        when(discoveryClient.fetchTopLiveStreams(anyInt())).thenReturn(List.of(targetCached, nonTarget));
        when(targetStreamPool.getAllActiveTargetChannels()).thenReturn(Set.of("ch1"));
        when(discoveryClient.fetchLiveStreams(Set.of("ch1"))).thenReturn(List.of(targetDetailed));
        when(streamRepository.getActiveChannelIds()).thenReturn(Collections.emptySet());
        when(streamRepository.getStreamTargets(anyList())).thenReturn(Collections.emptyList());
        when(streamUpdateAnalyzer.analyze(anyList(), anySet(), anyList(), any(Instant.class)))
            .thenReturn(new StreamUpdateResults(Set.of(targetDetailed), Collections.emptySet(), Collections.emptySet(), Instant.now()));

        ingestionService.ingest();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StreamSyncRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiServerClient).syncStreams(captor.capture());

        List<StreamSyncRequest> syncedRequests = captor.getValue();
        assertThat(syncedRequests).hasSize(2);

        StreamSyncRequest ch1Request = syncedRequests.stream()
            .filter(r -> r.streamId().equals("ch1"))
            .findFirst()
            .orElseThrow();
        assertThat(ch1Request.concurrentUserCount()).isEqualTo(3500);
        assertThat(ch1Request.startedAt()).isEqualTo(detailedStartedAt);

        StreamSyncRequest ch2Request = syncedRequests.stream()
            .filter(r -> r.streamId().equals("ch2"))
            .findFirst()
            .orElseThrow();
        assertThat(ch2Request.concurrentUserCount()).isEqualTo(500);
        assertThat(ch2Request.startedAt()).isNull();
    }
}
