package io.slice.stream.core.redis;

public final class Rediskeys {

    public static final String ANALYSIS_INDEX = "active:analysis:ids";
    public static final String STREAM_TARGETS = "stream:targets";
    public static final String STREAM_LIVE_HASH = "stream:live:";
    public static final String CHAT_AGGREGATION_PREFIX = "chat:aggregation:%s";
    public static final long CHAT_AGGREGATION_RETENTION = 604_800_000L;

    private Rediskeys() {
    }
}
