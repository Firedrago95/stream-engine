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
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("분석 윈도우 틱 수(windowTicks)는 1 이상이어야 합니다.");
        }
        if (maskingExclusionTicks < 0) {
            throw new IllegalArgumentException("마스킹 제외 틱 수(maskingExclusionTicks)는 0 이상이어야 합니다.");
        }
    }

    public boolean isMega() {
        return this.tier == StreamTier.MEGA;
    }
}
