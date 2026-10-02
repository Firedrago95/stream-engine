package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Builder;

@Builder
public record StreamTierInfo(
    String streamId,
    StreamTier tier,
    long minFirepowerCutoff,
    long noiseFloor,
    int windowSeconds,
    double zScoreThreshold,
    int maskingExclusionTicks
) {

    public boolean isMega() {
        return this.tier == StreamTier.MEGA;
    }
}
