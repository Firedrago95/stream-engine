package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.DynamicFloorProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.GroupProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    private static final int RECENT_WINDOW_TICKS = 600;

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
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        for (String streamId : activeStreamIds) {
            try {
                processTierUpdate(streamId, oneHourAgo, now);
            } catch (Exception e) {
                log.error("[TierManager] 스트림 {} 체급 갱신 중 에러 발생", streamId, e);
            }
        }
    }

    private void processTierUpdate(String streamId, Instant from, Instant to) {
        List<Long> lastHourDeltas = chatRepository.getFirepowerDeltas(streamId, from, to);
        if (lastHourDeltas.size() < 100) return;

        List<Long> recentDeltas = extractRecentDeltas(lastHourDeltas);
        double avgFirepower = calculateAverageFirepower(recentDeltas);
        long dynamicNoiseFloor = calculateDynamicNoiseFloor(avgFirepower);

        long percentile1Cutoff = calculatePercentile(lastHourDeltas, props.percentileCut());
        StreamTier tier = determineTier(recentDeltas);

        tierCache.put(streamId, buildTierInfo(streamId, tier, percentile1Cutoff, dynamicNoiseFloor));
    }

    private StreamTier determineTier(List<Long> recentDeltas) {
        if (recentDeltas.isEmpty()) {
            return StreamTier.MICRO;
        }

        double avgFirepower = calculateAverageFirepower(recentDeltas);
        long maxFirepower = calculateMaxFirepower(recentDeltas);

        if (avgFirepower >= props.tier().mega().conditionMinAvg() &&
            maxFirepower >= props.tier().mega().conditionMinPeak()) {
            return StreamTier.MEGA;
        }
        if (avgFirepower >= props.tier().regular().conditionMinAvg() &&
            maxFirepower >= props.tier().regular().conditionMinPeak()) {
            return StreamTier.REGULAR;
        }
        return StreamTier.MICRO;
    }

    private StreamTierInfo buildTierInfo(String streamId, StreamTier tier, long cutoff, long noiseFloor) {
        GroupProperties groupProps = switch (tier) {
            case MEGA -> props.tier().mega();
            case REGULAR -> props.tier().regular();
            case MICRO -> props.tier().micro();
        };

        return StreamTierInfo.builder()
            .streamId(streamId)
            .tier(tier)
            .minFirepowerCutoff(cutoff)
            .noiseFloor(noiseFloor)
            .windowSeconds(groupProps.windowSeconds())
            .zScoreThreshold(groupProps.zScore())
            .maskingExclusionTicks(props.getMaskingTickCount())
            .build();
    }

    private StreamTierInfo createColdStartTier(String streamId, int currentViewers) {
        long calculatedCutoff = (long) (currentViewers * props.coldStartWeight());
        long hardFloorCutoff = Math.max(props.dynamicFloor().minFloor(), calculatedCutoff);

        return buildTierInfo(streamId, StreamTier.MICRO, hardFloorCutoff, hardFloorCutoff);
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

    private long calculateMaxFirepower(List<Long> deltas) {
        return deltas.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0L);
    }

    private long calculatePercentile(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private List<Long> extractRecentDeltas(List<Long> allDeltas) {
        int startIndex = Math.max(0, allDeltas.size() - RECENT_WINDOW_TICKS);
        return allDeltas.subList(startIndex, allDeltas.size());
    }
}
