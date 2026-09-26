package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.domain.service.StreamUpdateAnalyzer;
import io.slice.stream.engine.ingestion.fake.FakeApiServerClient;
import io.slice.stream.engine.ingestion.fake.FakeApplicationEventPublisher;
import io.slice.stream.engine.ingestion.fake.FakeStreamDiscoveryClient;
import io.slice.stream.engine.ingestion.fake.FakeStreamRepository;
import io.slice.stream.engine.ingestion.fake.FakeTargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IngestionServiceTest {

    private FakeStreamDiscoveryClient discoveryClient;
    private FakeStreamRepository streamRepository;
    private FakeApiServerClient apiServerClient;
    private FakeApplicationEventPublisher eventPublisher;
    private StreamUpdateAnalyzer streamUpdateAnalyzer;
    private FakeTargetStreamPool targetStreamPool;

    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        discoveryClient = new FakeStreamDiscoveryClient();
        streamRepository = new FakeStreamRepository();
        apiServerClient = new FakeApiServerClient();
        eventPublisher = new FakeApplicationEventPublisher();
        streamUpdateAnalyzer = new StreamUpdateAnalyzer();
        targetStreamPool = new FakeTargetStreamPool();
        AsyncPromotionInspector asyncPromotionInspector = new AsyncPromotionInspector(
            discoveryClient,
            streamRepository,
            apiServerClient,
            Executors.newSingleThreadExecutor()
        );

        ingestionService = new IngestionService(
            discoveryClient,
            streamRepository,
            eventPublisher,
            apiServerClient,
            streamUpdateAnalyzer,
            targetStreamPool,
            asyncPromotionInspector
        );
    }

    @Test
    void 새로운_스트림과_종료된_스트림이_있을때_StreamChangedEvent를_한번만_발행해야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 123L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 124L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(streamTarget1));
        discoveryClient.addDetailedStream(streamTarget1);
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(streamTarget2));

        ingestionService.ingest();

        List<StreamChangedEvent> events = eventPublisher.getEventsOfType(StreamChangedEvent.class);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).newStreams()).containsExactly(streamTarget1);
        assertThat(events.get(0).closedStreams()).containsExactly(streamTarget2);
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(streamTarget1);
    }

    @Test
    void 변경되지_않은_스트림에_대해서는_이벤트를_발행하지_않아야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "TALK", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(streamTarget1));
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(streamTarget1));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(eventPublisher.getEventsOfType(StreamChangedEvent.class)).isEmpty();
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(streamTarget1);
    }

    @Test
    void 스트림_탐색_중_오류를_정상적으로_처리해야_한다() {
        discoveryClient.setThrowOnFetch(true);

        assertDoesNotThrow(() -> ingestionService.ingest());
        assertThat(apiServerClient.getSyncStreamsCallCount()).isZero();
        assertThat(streamRepository.getLastSyncedTargets()).isEmpty();
    }

    @Test
    void 저장소의_스트림_상태를_업데이트해야_한다() {
        StreamTarget streamTarget1 = new StreamTarget("ch1", "chName1", "chatCh1", 1L, "title1", 10, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("ch2", "chName2", "chatCh2", 2L, "title2", 20, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH);
        List<StreamTarget> liveStreams = List.of(streamTarget1, streamTarget2);

        discoveryClient.setTopLiveStreams(liveStreams);
        discoveryClient.addDetailedStream(streamTarget1);
        discoveryClient.addDetailedStream(streamTarget2);
        targetStreamPool.setTargetChannels(Set.of("ch1", "ch2"));

        ingestionService.ingest();

        assertThat(streamRepository.getLastSyncedTargets()).containsExactlyInAnyOrder(streamTarget1, streamTarget2);
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
    }

    @Test
    void 방송_상태_변화가_없더라도_API_서버_동기화는_항상_호출되어야_한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(target));
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(target));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
        assertThat(eventPublisher.getEventsOfType(StreamChangedEvent.class)).isEmpty();
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(target);
    }

    @Test
    void 방송_상태_변화가_있으면_동기화와_이벤트_발행_둘_다_수행한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(target));
        discoveryClient.addDetailedStream(target);
        targetStreamPool.setTargetChannels(Set.of("ch1"));

        ingestionService.ingest();

        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
        List<StreamChangedEvent> events = eventPublisher.getEventsOfType(StreamChangedEvent.class);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).newStreams()).containsExactly(target);
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(target);
    }

    @Test
    void 메타데이터_변경이_감지되면_API_서버에_세그먼트_기록을_전송해야_한다() {
        StreamTarget cachedTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);
        StreamTarget changedLiveTarget = new StreamTarget("ch1", "이름", null, 1L, "변경된 제목", 150, "url", "cat", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(changedLiveTarget));
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(cachedTarget));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(apiServerClient.getRecordSegmentsCallCount()).isEqualTo(1);
        List<ChangedStream> recorded = apiServerClient.getLastRecordedSegments();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).streamId()).isEqualTo("ch1");
        assertThat(recorded.get(0).newTitle()).isEqualTo("변경된 제목");
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(changedLiveTarget.withChatChannelId("chat1"));
    }

    @Test
    void 메타데이터_변경이_없으면_API_서버에_세그먼트_기록을_전송하지_않아야_한다() {
        StreamTarget dummyTarget = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 100, "url", "cat", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(dummyTarget));
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(dummyTarget));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(apiServerClient.getRecordSegmentsCallCount()).isZero();
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(dummyTarget);
    }

    @Test
    void 전체_방송_중_타겟_명단에_포함된_채널만_상세_조회하여_저장소에_동기화한다() {
        StreamTarget targetStream = new StreamTarget("target1", "타겟스트리머", null, 1L, "타겟제목", 100, "url1", "GAME", null);
        StreamTarget nonTargetStream = new StreamTarget("normal1", "일반스트리머", null, 2L, "일반제목", 10, "url2", "TALK", null);
        StreamTarget detailedTarget = new StreamTarget("target1", "타겟스트리머", "chat1", 1L, "타겟제목", 100, "url1", "GAME", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(targetStream, nonTargetStream));
        discoveryClient.addDetailedStream(detailedTarget);
        targetStreamPool.setTargetChannels(Set.of("target1"));

        ingestionService.ingest();

        assertThat(discoveryClient.getRequestedDetailChannelIds()).containsExactly("target1");
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(detailedTarget);
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
    }

    @Test
    void 타겟_명단에_포함된_방송이_없으면_상세_조회를_호출하지_않아야_한다() {
        StreamTarget nonTargetStream = new StreamTarget("normal1", "일반스트리머", null, 2L, "일반제목", 10, "url2", "TALK", null);

        discoveryClient.setTopLiveStreams(List.of(nonTargetStream));
        targetStreamPool.setTargetChannels(Set.of("target1"));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(streamRepository.getLastSyncedTargets()).isEmpty();
        assertThat(apiServerClient.getSyncStreamsCallCount()).isEqualTo(1);
    }

    @Test
    void 과거_활성_채널_목록을_기반으로_이전_스트림_상세_정보를_조회해야_한다() {
        StreamTarget target = new StreamTarget("ch1", "이름", "chat1", 1L, "제목", 10, "url", "GAME", Instant.EPOCH);
        StreamTarget closedTarget = new StreamTarget("ch_closed", "종료스트리머", "chatClosed", 99L, "종료제목", 0, "url", "GAME", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(target));
        discoveryClient.addDetailedStream(target);
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(target, closedTarget));

        ingestionService.ingest();

        assertThat(streamRepository.getLastClosedStreams()).containsExactly(closedTarget);
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(target);
    }

    @Test
    void 타겟_채널에_대해서는_상세_조회된_실시간_시청자수와_메타데이터로_병합하여_API_서버에_동기화한다() {
        Instant detailedStartedAt = Instant.parse("2026-09-15T10:00:00Z");
        StreamTarget targetCached = new StreamTarget("ch1", "침착맨", null, 1001L, "침착맨 방송", 1000, "url1", "소통", null);
        StreamTarget nonTarget = new StreamTarget("ch2", "비타겟", null, 1002L, "일반 방송", 500, "url2", "게임", null);
        StreamTarget targetDetailed = new StreamTarget("ch1", "침착맨", "chatCh1", 1001L, "침착맨 방송", 3500, "url1", "소통", detailedStartedAt);

        discoveryClient.setTopLiveStreams(List.of(targetCached, nonTarget));
        discoveryClient.addDetailedStream(targetDetailed);
        targetStreamPool.setTargetChannels(Set.of("ch1"));

        ingestionService.ingest();

        List<StreamSyncRequest> syncedRequests = apiServerClient.getLastSyncedRequests();
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

    @Test
    void 이미_추적_중인_동일_세션_스트림은_상세_API를_호출하지_않고_캐시된_chatChannelId를_재사용한다() {
        StreamTarget cachedTarget = new StreamTarget("ch1", "침착맨", "chatCh1", 1001L, "과거제목", 1000, "url1", "소통", Instant.EPOCH);
        StreamTarget liveTarget = new StreamTarget("ch1", "침착맨", null, 1001L, "최신제목", 2500, "url1", "소통", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(liveTarget));
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(cachedTarget));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isZero();
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(liveTarget.withChatChannelId("chatCh1"));
    }

    @Test
    void 동일_채널이더라도_liveId가_변경된_새_방송은_상세_API를_호출한다() {
        StreamTarget cachedTarget = new StreamTarget("ch1", "침착맨", "chatCh1_old", 1001L, "과거방송", 1000, "url1", "소통", Instant.EPOCH);
        StreamTarget newLiveTarget = new StreamTarget("ch1", "침착맨", null, 1002L, "새로운방송", 3000, "url1", "게임", Instant.EPOCH);
        StreamTarget detailedNewTarget = new StreamTarget("ch1", "침착맨", "chatCh1_new", 1002L, "새로운방송", 3000, "url1", "게임", Instant.EPOCH);

        discoveryClient.setTopLiveStreams(List.of(newLiveTarget));
        discoveryClient.addDetailedStream(detailedNewTarget);
        targetStreamPool.setTargetChannels(Set.of("ch1"));
        streamRepository.setActiveTargets(List.of(cachedTarget));

        ingestionService.ingest();

        assertThat(discoveryClient.getFetchLiveStreamsCallCount()).isEqualTo(1);
        assertThat(discoveryClient.getRequestedDetailChannelIds()).containsExactly("ch1");
        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(detailedNewTarget);
    }

    @Test
    void 성인_방송은_웹소켓_수집_대상에서_제외되지만_API_서버_동기화_대상에는_포함된다() {
        StreamTarget normalTarget = new StreamTarget("ch1", "일반스트리머", "chatCh1", 101L, "일반방송", 1000, "https://thumb.com/ch1.jpg", "GAME", Instant.EPOCH, false);
        StreamTarget adultTarget = new StreamTarget("ch2", "성인스트리머", "chatCh2", 102L, "19금방송", 2000, "https://thumb.com/ch2.jpg", "GAME", Instant.EPOCH, true);

        discoveryClient.setTopLiveStreams(List.of(normalTarget, adultTarget));
        discoveryClient.addDetailedStream(normalTarget);
        targetStreamPool.setTargetChannels(Set.of("ch1", "ch2"));

        ingestionService.ingest();

        List<StreamSyncRequest> syncedRequests = apiServerClient.getLastSyncedRequests();
        assertThat(syncedRequests).hasSize(2);
        assertThat(syncedRequests.stream().map(StreamSyncRequest::streamId).toList())
            .containsExactlyInAnyOrder("ch1", "ch2");

        assertThat(streamRepository.getLastSyncedTargets()).containsExactly(normalTarget);
    }
}
