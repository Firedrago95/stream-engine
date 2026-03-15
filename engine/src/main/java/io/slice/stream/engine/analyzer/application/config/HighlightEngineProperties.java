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
    TierProperties tier
) {
    public record TierProperties(GroupProperties groupA, GroupProperties groupB) {}

    public record GroupProperties(
        int windowSeconds,
        double zScore,
        double conditionMinAvg,
        int conditionMinPeak
    ) {}

    // YAML의 마스킹 시간(12s)을 스케줄러 주기(3s)로 나눠서 배제할 '틱(Tick)' 수를 스스로 계산
    public int getMaskingTickCount() {
        return (int) (maskingTimeMs / schedulerIntervalMs);
    }
}
