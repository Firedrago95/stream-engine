package io.slice.stream.engine.analyzer.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "highlight.engine")
public record HighlightEngineProperties(
    long schedulerIntervalMs,
    long managerRefreshMs,
    long aggregationIntervalMs,
    long recentWindowMs,
    long minDataMs,
    int fetchBufferSeconds,
    double coldStartWeight,
    DynamicFloorProperties dynamicFloor
) {

    public HighlightEngineProperties {
        if (aggregationIntervalMs <= 0) {
            throw new IllegalArgumentException("데이터 집계 간격(aggregationIntervalMs)은 1ms 이상이어야 합니다.");
        }
        if (recentWindowMs <= 0) {
            recentWindowMs = 1_800_000L;
        }
        if (minDataMs <= 0) {
            minDataMs = 300_000L;
        }
        if (dynamicFloor == null) {
            dynamicFloor = new DynamicFloorProperties(5L, 5.0, 4.0);
        }
    }

    public record DynamicFloorProperties(
        long minFloor,
        double slope,
        double intercept
    ) {}

    public int getRecentWindowTickCount() {
        return (int) (recentWindowMs / aggregationIntervalMs);
    }

    public int getMinDataPointCount() {
        return (int) (minDataMs / aggregationIntervalMs);
    }
}
