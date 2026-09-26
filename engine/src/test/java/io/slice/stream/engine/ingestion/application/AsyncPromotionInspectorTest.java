package io.slice.stream.engine.ingestion.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.fake.FakeApiServerClient;
import io.slice.stream.engine.ingestion.fake.FakeStreamDiscoveryClient;
import io.slice.stream.engine.ingestion.fake.FakeStreamRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsyncPromotionInspectorTest {

    @Test
    @DisplayName("방제 변경 스트림에 대해 비동기로 치지직 상세 API를 조회하고 상태가 다르면 보정 세그먼트를 전송하고 저장소 상태를 갱신한다")
    void shouldInspectAndCorrectSegmentWhenPaidPromotionDiffers() throws Exception {
        FakeStreamDiscoveryClient discoveryClient = new FakeStreamDiscoveryClient();
        FakeStreamRepository streamRepository = new FakeStreamRepository();
        FakeApiServerClient apiServerClient = new FakeApiServerClient();
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        StreamTarget detailedTarget = new StreamTarget(
            "ch1", "스트리머1", "chat1", 100L, "신작게임", 500, null, "게임", Instant.EPOCH, false, true
        );
        discoveryClient.addDetailedStream(detailedTarget);
        streamRepository.addActiveTarget(detailedTarget.withPaidPromotion(false));

        AsyncPromotionInspector inspector = new AsyncPromotionInspector(
            discoveryClient,
            streamRepository,
            apiServerClient,
            executorService
        );

        ChangedStream changedStream = new ChangedStream(
            "ch1", "100", "이전방제", "신작게임", "저챗", "게임", Instant.now(), 5000L, false
        );

        inspector.inspectChangedStreamsAsync(List.of(changedStream));

        executorService.shutdown();
        boolean finished = executorService.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(apiServerClient.getLastRecordedSegments()).hasSize(1);
        ChangedStream recorded = apiServerClient.getLastRecordedSegments().get(0);
        assertThat(recorded.streamId()).isEqualTo("ch1");
        assertThat(recorded.paidPromotion()).isTrue();
        assertThat(streamRepository.getStreamTargets(List.of("ch1")).get(0).paidPromotion()).isTrue();
    }

    @Test
    @DisplayName("치지직 상세 API 조회 결과와 기존 광고 상태가 동일하면 추가 세그먼트 전송을 하지 않는다")
    void shouldNotSendSegmentWhenPaidPromotionMatches() throws Exception {
        FakeStreamDiscoveryClient discoveryClient = new FakeStreamDiscoveryClient();
        FakeStreamRepository streamRepository = new FakeStreamRepository();
        FakeApiServerClient apiServerClient = new FakeApiServerClient();
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        StreamTarget detailedTarget = new StreamTarget(
            "ch1", "스트리머1", "chat1", 100L, "롤 솔랭", 500, null, "롤", Instant.EPOCH, false, false
        );
        discoveryClient.addDetailedStream(detailedTarget);
        streamRepository.addActiveTarget(detailedTarget);

        AsyncPromotionInspector inspector = new AsyncPromotionInspector(
            discoveryClient,
            streamRepository,
            apiServerClient,
            executorService
        );

        ChangedStream changedStream = new ChangedStream(
            "ch1", "100", "숙제방송", "롤 솔랭", "게임", "롤", Instant.now(), 5000L, false
        );

        inspector.inspectChangedStreamsAsync(List.of(changedStream));

        executorService.shutdown();
        boolean finished = executorService.awaitTermination(3, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(apiServerClient.getLastRecordedSegments()).isEmpty();
    }
}
