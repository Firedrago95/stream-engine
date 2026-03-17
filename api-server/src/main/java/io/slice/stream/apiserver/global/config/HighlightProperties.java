package io.slice.stream.apiserver.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "highlight")
public record HighlightProperties (
    // 1. 영상 구간 설정 (Duration 사용 시 '20s' 등을 자동으로 파싱)
    Duration leadingBuffer,      // 피크 발생 전 시작 여유분
    Duration trailingBuffer,     // 피크 발생 후 종료 여유분
    Duration cooldown,           // 하이라이트 병합/무시 억제 기간

    // 2. 분석 알고리즘 변수
    double extensionRatio,       // 동적 슬라이딩 윈도우 확장을 위한 여진 비율
    int minimum,                 // 하이라이트 생성을 위한 최소 화력 임계값

    // 3. 노출 및 데이터 관리 정책
    int realtimeLimit,           // 실시간 탭 노출 개수
    int historyDisplayLimit,     // 과거 방송 탭(24시간 내) 노출 개수
    int cleanupRetentionLimit    // 방종 24시간 후 DB 유지 개수 (Top N)
) {}
