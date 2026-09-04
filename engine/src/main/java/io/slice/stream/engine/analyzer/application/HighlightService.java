package io.slice.stream.engine.analyzer.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.detection.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.signal.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.signal.HighlightSignalClient;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.core.model.StreamTarget;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HighlightService {

    private final ActiveStreamProvider streamProvider;
    private final ExecutorService virtualThreadExecutor;
    private final HighlightSignalClient signalClient;
    private final Clock clock;
    private final StreamTierManager tierManager;
    private final ChatRoomAggregationRepository repository;
    private final HighlightDetector detector;
    private final HighlightEngineProperties props;
    private final Timer analysisTimer;
    private final Counter peakDetectedCounter;

    public HighlightService(
        ActiveStreamProvider streamProvider,
        ExecutorService virtualThreadExecutor,
        HighlightSignalClient signalClient,
        Clock clock,
        StreamTierManager tierManager,
        ChatRoomAggregationRepository repository,
        HighlightDetector detector,
        HighlightEngineProperties props,
        MeterRegistry meterRegistry
    ) {
        this.streamProvider = streamProvider;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.signalClient = signalClient;
        this.clock = clock;
        this.tierManager = tierManager;
        this.repository = repository;
        this.detector = detector;
        this.props = props;
        this.analysisTimer = Timer.builder("engine.analysis.duration")
            .description("3초 주기 분석 연산 소요 시간")
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
        this.peakDetectedCounter = Counter.builder("engine.peaks.detected")
            .description("감지된 하이라이트 PEAK 시그널 수")
            .register(meterRegistry);
    }

    @Scheduled(fixedRateString = "${highlight.engine.scheduler-interval-ms}")
    public void monitorHighlights() {
        analysisTimer.record(() -> {
            List<StreamTarget> activeTargets = streamProvider.getActiveStreamTargets();
            validateTrafficLoad(activeTargets.size());

            List<CompletableFuture<Optional<AnalysisSignal>>> futures = activeTargets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> processStream(target), virtualThreadExecutor))
                .toList();

            List<AnalysisSignal> signals = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList();

            if (!signals.isEmpty()) {
                signalClient.send(signals);
            }
        });
    }

    private void validateTrafficLoad(int targetCount) {
        if (targetCount > 500) {
            log.warn("[Thundering Herd Warning] 활성 스트림이 {}개 입니다. Redis 부하 방지를 고려하세요.", targetCount);
        }
    }

    private Optional<AnalysisSignal> processStream(StreamTarget target) {
        String streamId = target.channelId();
        int currentViewers = target.concurrentUserCount();

        try {
            Instant now = clock.instant();
            StreamTierInfo tierInfo = tierManager.getTierInfo(streamId, currentViewers);

            // 분석에 필요한 델타 데이터 리스트 조회
            List<Long> deltas = fetchDeltas(streamId, now, tierInfo);

            // 감지 로직 실행
            DetectionResult result = detector.detect(streamId, deltas, tierInfo);

            return convertToSignal(target, result, now, tierInfo);
        } catch (Exception e) {
            log.error("[Analysis-Error] 분석 중 예외 발생: {}", streamId, e);
            return Optional.empty();
        }
    }

    private List<Long> fetchDeltas(String streamId, Instant now, StreamTierInfo tierInfo) {
        // 체급별 윈도우 사이즈와 여유분을 합산하여 데이터 조회
        Instant from = now.minusSeconds(tierInfo.windowSeconds() + props.fetchBufferSeconds());
        return repository.getFirepowerDeltas(streamId, from, now);
    }

    private Optional<AnalysisSignal> convertToSignal(StreamTarget target, DetectionResult result, Instant now, StreamTierInfo tierInfo) {
        Long offsetMs = null;
        if (target.startedAt() != null) {
            offsetMs = now.toEpochMilli() - target.startedAt().toEpochMilli();
        }

        String streamId = target.channelId();
        String liveId = String.valueOf(target.liveId());
        if (result.status() == ChatFirepowerStatus.WAITING) {
            return Optional.of(new AnalysisSignal(streamId, liveId, ChatFirepowerStatus.NORMAL.name(), now,
                result.firepower() != null ? result.firepower() : 0L, offsetMs));
        }

        if (result.status() == ChatFirepowerStatus.PEAK) {
            peakDetectedCounter.increment();
            log.info("[Analysis] PEAK 시그널 전송 - Stream: {}, 수치: {}, 체급: {}",
                streamId, result.firepower(), tierInfo.tier().name());
        }

        return Optional.of(new AnalysisSignal(streamId, liveId, result.status().name(), now, result.firepower(), offsetMs));
    }
}
