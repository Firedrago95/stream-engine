package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Builder;

@Builder
public record StreamTierInfo(
    String streamId,
    long noiseFloor
) {

    public StreamTierInfo {
        if (noiseFloor < 0) {
            throw new IllegalArgumentException("노이즈 바닥값은 0 이상이어야 합니다.");
        }
    }
}
