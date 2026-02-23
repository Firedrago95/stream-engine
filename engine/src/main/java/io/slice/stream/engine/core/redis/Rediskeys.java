package io.slice.stream.engine.core.redis;

public final class Rediskeys {

    // 분석 엔진 전용 인덱스 키
    public static final String ANALYSIS_INDEX = "active:analysis:ids";

    // 방송 상태 관련 키
    public static final String STREAM_TARGETS = "stream:targets";
    public static final String STREAM_LIVE_HASH = "stream:live:";
    public static final String CHAT_AGGREGATION_PREFIX = "chat:aggregation:%s";

    // 리텐션 정책
    public static final long CHAT_AGGREGATION_RETENTION = 604_800_000L;

    private Rediskeys() {}
}
