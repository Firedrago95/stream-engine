package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamTierManager {

    private final ActiveStreamProvider activeStreamProvider;
    private final ChatRoomAggregationRepository chatRepository;
    private final HighlightEngineProperties props;

    private final Cache<String, StreamTierInfo> tierCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(2000)
        .build();

    public StreamTierInfo getTierInfo(String streamId, int currentViewers) {
        StreamTierInfo info = tierCache.getIfPresent(streamId);
        if (info != null) {
            return info;
        }
        return createColdStartTier(streamId, currentViewers);
    }

    @Scheduled(fixedRateString = "${highlight.engine.manager-refresh-ms}")
    public void refreshAllTiers() {
        List<String> activeStreamIds = activeStreamProvider.getActiveStreamIds();
        Instant now = Instant.now();
        Instant windowStart = now.minusMillis(props.recentWindowMs());

        for (String streamId : activeStreamIds) {
            try {
                processTierUpdate(streamId, windowStart, now);
            } catch (Exception e) {
                log.error("[TierManager] 스트림 {} 바닥값 갱신 중 에러 발생", streamId, e);
            }
        }
    }

    private void processTierUpdate(String streamId, Instant from, Instant to) {
        List<Long> recentDeltas = chatRepository.getFirepowerDeltas(streamId, from, to);
        if (recentDeltas.size() < props.getMinDataPointCount()) {
            return;
        }

        double avgFirepower = calculateAverageFirepower(recentDeltas);
        long dynamicNoiseFloor = calculateDynamicNoiseFloor(avgFirepower);

        tierCache.put(streamId, new StreamTierInfo(streamId, dynamicNoiseFloor));
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
