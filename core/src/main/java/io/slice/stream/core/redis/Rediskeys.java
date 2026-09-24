package io.slice.stream.core.redis;

public final class Rediskeys {

    public static final String ANALYSIS_INDEX = "active:analysis:ids";
    public static final String STREAM_TARGETS = "stream:targets";
    public static final String STREAM_LIVE_HASH = "stream:live:";
    public static final String CHAT_AGGREGATION_PREFIX = "chat:aggregation:%s";
    public static final long CHAT_AGGREGATION_RETENTION = 604_800_000L;
    public static final String CHAT_SUMMARY_PREFIX = "chat:summary:%s";
    public static final long CHAT_SUMMARY_TTL_SECONDS = 86400L;
    public static final String CHAT_SUMMARY_FIELD_TOTAL = "totalChatCount";
    public static final String CHAT_SUMMARY_FIELD_SUBSCRIBER = "subscriberChatCount";

    private Rediskeys() {
    }
}
