package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StreamTier {
    MEGA,
    REGULAR,
    MICRO
}
