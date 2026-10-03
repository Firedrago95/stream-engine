package io.slice.stream.engine.analyzer.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "highlight.engine")
public record HighlightEngineProperties(
    long schedulerIntervalMs,
    long managerRefreshMs,
    long aggregationIntervalMs,
    long maskingTimeMs,
    int fetchBufferSeconds,
    double percentileCut,
    double coldStartWeight,
    TierProperties tier,
    DynamicFloorProperties dynamicFloor
) {

    private static final long RECENT_WINDOW_MS = 1_800_000L;
    private static final long MIN_TIER_DATA_MS = 300_000L;

    public HighlightEngineProperties {
        if (aggregationIntervalMs <= 0) {
            throw new IllegalArgumentException("데이터 집계 간격(aggregationIntervalMs)은 1ms 이상이어야 합니다.");
        }
        if (dynamicFloor == null) {
            dynamicFloor = new DynamicFloorProperties(5L, 4.0, 4.0);
        }
    }

    public record TierProperties(
        GroupProperties mega,
        GroupProperties regular,
        GroupProperties micro
    ) {}

    public record GroupProperties(
        int windowSeconds,
        double zScore,
        double conditionMinAvg,
        int conditionMinPeak,
        long noiseFloor
    ) {}

    public record DynamicFloorProperties(
        long minFloor,
        double slope,
        double intercept
    ) {}

    public int getMaskingTickCount() {
        return (int) (maskingTimeMs / aggregationIntervalMs);
    }

    public int getRecentWindowTickCount() {
        return (int) (RECENT_WINDOW_MS / aggregationIntervalMs);
    }

    public int getMinTierDataPointCount() {
        return (int) (MIN_TIER_DATA_MS / aggregationIntervalMs);
    }

    public int getWindowTickCount(int windowSeconds) {
        return (int) (windowSeconds * 1000L / aggregationIntervalMs);
    }
}
