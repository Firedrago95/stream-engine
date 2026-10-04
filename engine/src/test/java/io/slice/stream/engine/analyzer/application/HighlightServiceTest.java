package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.signal.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.analyzer.fake.DirectExecutorService;
import io.slice.stream.engine.analyzer.fake.FakeActiveStreamProvider;
import io.slice.stream.engine.analyzer.fake.FakeChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.fake.FakeHighlightDetector;
import io.slice.stream.engine.analyzer.fake.FakeHighlightSignalClient;
import io.slice.stream.engine.analyzer.fake.FakeStreamTierManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-02-13T10:00:00Z");

    private FakeActiveStreamProvider streamProvider;
    private DirectExecutorService executor;
    private FakeHighlightSignalClient signalClient;
    private FakeStreamTierManager tierManager;
    private FakeChatRoomAggregationRepository repository;
    private FakeHighlightDetector detector;
    private HighlightEngineProperties props;
    private Clock clock;
    private MeterRegistry meterRegistry;

    private HighlightService highlightService;

    @BeforeEach
    void setUp() {
        streamProvider = new FakeActiveStreamProvider();
        executor = new DirectExecutorService();
        signalClient = new FakeHighlightSignalClient();
        tierManager = new FakeStreamTierManager();
        repository = new FakeChatRoomAggregationRepository();
        detector = new FakeHighlightDetector();
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        meterRegistry = new SimpleMeterRegistry();

        props = new HighlightEngineProperties(
            3000L,
            60000L,
            3000L,
            1800000L,
            300000L,
            15,
            0.05,
            new DynamicFloorProperties(5L, 5.0, 4.0)
        );

        highlightService = new HighlightService(
            streamProvider,
            executor,
            signalClient,
            clock,
            tierManager,
            repository,
            detector,
            props,
            meterRegistry
        );
    }

    @Test
    void 모든_파이프라인이_성공적으로_동작하여_PEAK_신호를_전송한다() {
        // given
        StreamTarget target = new StreamTarget("stream1", "침착맨", "chat1", 1L, "title", 1000, "url", "게임", Instant.EPOCH);
        streamProvider.setTargets(List.of(target));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder()
            .streamId("stream1")
            .noiseFloor(12L)
            .build();
        tierManager.setTierInfo("stream1", mockTierInfo);

        repository.setFirepowerDeltas("stream1", List.of(1L, 2L, 50L));
        detector.setStreamResult("stream1", new DetectionResult(ChatFirepowerStatus.PEAK, 50L));

        // when
        highlightService.monitorHighlights();

        // then
        List<AnalysisSignal> sentSignals = signalClient.getLastSentSignals();
        assertThat(sentSignals).hasSize(1);
        assertThat(sentSignals.get(0).status()).isEqualTo("PEAK");
        assertThat(sentSignals.get(0).firepower()).isEqualTo(50L);
        assertThat(sentSignals.get(0).liveId()).isEqualTo(String.valueOf(target.liveId()));
    }

    @Test
    void WAITING_상태는_NORMAL로_둔갑하여_차트_렌더링용으로_전송된다() {
        // given
        StreamTarget target = new StreamTarget("stream-wait", "하꼬방", "chat1", 1L, "title", 10, "url", "소통", Instant.EPOCH);
        streamProvider.setTargets(List.of(target));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder()
            .streamId("stream-wait")
            .noiseFloor(6L)
            .build();
        tierManager.setTierInfo("stream-wait", mockTierInfo);

        repository.setFirepowerDeltas("stream-wait", List.of(1L, 2L));
        detector.setStreamResult("stream-wait", DetectionResult.waiting());

        // when
        highlightService.monitorHighlights();

        // then
        List<AnalysisSignal> sentSignals = signalClient.getLastSentSignals();
        assertThat(sentSignals).hasSize(1);
        assertThat(sentSignals.get(0).status()).isEqualTo("NORMAL");
    }

    @Test
    void startedAt이_null인_스트림은_신호를_생성하지_않아야_한다() {
        // given
        StreamTarget targetWithoutStartedAt = new StreamTarget("stream-no-start", "방", "chat1", 1L, "title", 10, "url", "소통", null);
        streamProvider.setTargets(List.of(targetWithoutStartedAt));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder()
            .streamId("stream-no-start")
            .noiseFloor(6L)
            .build();
        tierManager.setTierInfo("stream-no-start", mockTierInfo);

        repository.setFirepowerDeltas("stream-no-start", List.of(1L, 2L));
        detector.setStreamResult("stream-no-start", new DetectionResult(ChatFirepowerStatus.PEAK, 50L));

        // when
        highlightService.monitorHighlights();

        // then
        assertThat(signalClient.getSentCount()).isEqualTo(0);
    }

    @Test
    void 신호가_존재할때_실행기를_통해_신호_클라이언트로_전송된다() {
        // given
        StreamTarget target = new StreamTarget("stream1", "침착맨", "chat1", 1L, "title", 1000, "url", "게임", Instant.EPOCH);
        streamProvider.setTargets(List.of(target));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder()
            .streamId("stream1")
            .noiseFloor(12L)
            .build();
        tierManager.setTierInfo("stream1", mockTierInfo);

        repository.setFirepowerDeltas("stream1", List.of(1L, 2L, 50L));
        detector.setStreamResult("stream1", new DetectionResult(ChatFirepowerStatus.PEAK, 50L));

        // when
        highlightService.monitorHighlights();

        // then
        assertThat(signalClient.getSentCount()).isEqualTo(1);
        assertThat(signalClient.getLastSentSignals()).hasSize(1);
        assertThat(executor.getExecutedTaskCount()).isGreaterThan(0);
    }
}
