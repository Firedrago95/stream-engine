package io.slice.stream.engine.analyzer.application.config;

import java.util.Objects;
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
        if (schedulerIntervalMs <= 0) {
            throw new IllegalArgumentException("엔진 분석 스케줄러 주기(schedulerIntervalMs)는 1ms 이상이어야 합니다.");
        }
        if (managerRefreshMs <= 0) {
            throw new IllegalArgumentException("매니저 바닥값 갱신 주기(managerRefreshMs)는 1ms 이상이어야 합니다.");
        }
        if (aggregationIntervalMs <= 0) {
            throw new IllegalArgumentException("데이터 집계 간격(aggregationIntervalMs)은 1ms 이상이어야 합니다.");
        }
        if (recentWindowMs <= 0) {
            throw new IllegalArgumentException("평균 화력 산출 윈도우(recentWindowMs)는 1ms 이상이어야 합니다.");
        }
        if (minDataMs <= 0) {
            throw new IllegalArgumentException("최소 데이터 누적 시간(minDataMs)은 1ms 이상이어야 합니다.");
        }
        if (fetchBufferSeconds <= 0) {
            throw new IllegalArgumentException("Redis 조회 여유분(fetchBufferSeconds)은 1초 이상이어야 합니다.");
        }
        Objects.requireNonNull(dynamicFloor, "동적 바닥값 설정(dynamicFloor)은 필수입니다.");
    }

    public record DynamicFloorProperties(
        long minFloor,
        double slope,
        double intercept
    ) {
        public DynamicFloorProperties {
            if (minFloor < 0) {
                throw new IllegalArgumentException("최소 바닥값(minFloor)은 0 이상이어야 합니다.");
            }
            if (slope <= 0) {
                throw new IllegalArgumentException("평균 화력 비례 계수(slope)는 0보다 커야 합니다.");
            }
        }
    }

    public int getRecentWindowTickCount() {
        return (int) (recentWindowMs / aggregationIntervalMs);
    }

    public int getMinDataPointCount() {
        return (int) (minDataMs / aggregationIntervalMs);
    }
}
