package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Builder;

@Builder
public record StreamTierInfo(
    String streamId,
    StreamTier tier,
    long minFirepowerCutoff,
    int windowSeconds,
    double zScoreThreshold,
    int maskingExclusionTicks
) {

    public boolean isGroupA() {
        return this.tier == StreamTier.GROUP_A;
    }
}
