package io.slice.stream.engine.analyzer.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "highlight.engine")
public record HighlightEngineProperties(
    long schedulerIntervalMs,
    long managerRefreshMs,
    long maskingTimeMs,
    int fetchBufferSeconds,
    double percentileCut,
    double coldStartWeight,
    TierProperties tier,
    DynamicFloorProperties dynamicFloor
) {

    public HighlightEngineProperties {
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
        return (int) (maskingTimeMs / schedulerIntervalMs);
    }
}
