package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.SessionTierRepository;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StreamTierManager {

    private final ActiveStreamProvider activeStreamProvider;
    private final ChatRoomAggregationRepository chatRepository;
    private final SessionTierRepository sessionTierRepository;
    private final HighlightEngineProperties props;
    private final Cache<String, StreamTierInfo> tierCache;

    public StreamTierManager(
        ActiveStreamProvider activeStreamProvider,
        ChatRoomAggregationRepository chatRepository,
        SessionTierRepository sessionTierRepository,
        HighlightEngineProperties props
    ) {
        this.activeStreamProvider = activeStreamProvider;
        this.chatRepository = chatRepository;
        this.sessionTierRepository = sessionTierRepository;
        this.props = props;
        long retentionMs = props != null ? props.reLiveRetentionMs() : 420_000L;
        this.tierCache = Caffeine.newBuilder()
            .expireAfterWrite(retentionMs, TimeUnit.MILLISECONDS)
            .maximumSize(2000)
            .build();
    }

    public StreamTierInfo getTierInfo(String streamId, long liveId, int currentViewers) {
        StreamTierInfo info = tierCache.getIfPresent(streamId);
        if (info != null) {
            if (liveId > 0) {
                sessionTierRepository.save(liveId, info.noiseFloor());
            }
            return info;
        }

        if (liveId > 0) {
            Optional<Long> persistedFloor = sessionTierRepository.findByLiveId(liveId);
            if (persistedFloor.isPresent()) {
                long floor = persistedFloor.get();
                StreamTierInfo restored = new StreamTierInfo(streamId, floor);
                tierCache.put(streamId, restored);
                log.info("[TierManager] 스트림 {} (세션 {}) 이전 동적 바닥값({}) 복원 적용", streamId, liveId, floor);
                return restored;
            }
        }

        return createColdStartTier(streamId, currentViewers);
    }

    public StreamTierInfo getTierInfo(String streamId, int currentViewers) {
        return getTierInfo(streamId, 0L, currentViewers);
    }

    @Scheduled(fixedRateString = "${highlight.engine.manager-refresh-ms}")
    public void refreshAllTiers() {
        List<StreamTarget> activeTargets = activeStreamProvider.getActiveStreamTargets();
        Instant now = Instant.now();
        Instant windowStart = now.minusMillis(props.recentWindowMs());

        for (StreamTarget target : activeTargets) {
            try {
                processTierUpdate(target, windowStart, now);
            } catch (Exception e) {
                log.error("[TierManager] 스트림 {} 바닥값 갱신 중 에러 발생", target.channelId(), e);
            }
        }
    }

    private void processTierUpdate(StreamTarget target, Instant from, Instant to) {
        String streamId = target.channelId();
        List<Long> recentDeltas = chatRepository.getFirepowerDeltas(streamId, from, to);
        if (recentDeltas.size() < props.getMinDataPointCount()) {
            return;
        }

        double avgFirepower = calculateAverageFirepower(recentDeltas);
        long dynamicNoiseFloor = calculateDynamicNoiseFloor(avgFirepower);

        tierCache.put(streamId, new StreamTierInfo(streamId, dynamicNoiseFloor));
        if (target.liveId() > 0) {
            sessionTierRepository.save(target.liveId(), dynamicNoiseFloor);
        }
    }

    private StreamTierInfo createColdStartTier(String streamId, int currentViewers) {
        long f0 = calculateF0(currentViewers);
        return new StreamTierInfo(streamId, f0);
    }

    private long calculateF0(int currentViewers) {
        if (currentViewers >= 5000) {
            return 40L;
        }
        if (currentViewers >= 1000) {
            return 20L;
        }
        return 8L;
    }

    private long calculateDynamicNoiseFloor(double avgFirepower) {
        DynamicFloorProperties config = props.dynamicFloor();
        long calculated = Math.round(config.slope() * avgFirepower + config.intercept());
        return Math.max(config.minFloor(), calculated);
    }

    private double calculateAverageFirepower(List<Long> deltas) {
        return deltas.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
    }
}
