package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Builder;

@Builder
public record StreamTierInfo(
    String streamId,
    StreamTier tier,
    long minFirepowerCutoff,
    long noiseFloor,
    int windowSeconds,
    int windowTicks,
    double zScoreThreshold,
    int maskingExclusionTicks
) {

    public StreamTierInfo {
        if (windowTicks <= 0 && windowSeconds > 0) {
            windowTicks = Math.max(1, windowSeconds / 3);
        }
        if (maskingExclusionTicks <= 0) {
            maskingExclusionTicks = 4;
        }
    }

    public boolean isMega() {
        return this.tier == StreamTier.MEGA;
    }
}
