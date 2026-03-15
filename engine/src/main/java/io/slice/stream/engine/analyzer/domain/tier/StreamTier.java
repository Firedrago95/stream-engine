package io.slice.stream.engine.analyzer.domain.tier;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StreamTier {
    // 대형 화력 방송
    GROUP_A,
    // 중,소형 화력 방송
    GROUP_B
}
