package io.slice.stream.engine.analyzer.domain.aggregation;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ChatRoomAggregation {

    private final String streamId;
    private final AtomicReference<Instant> lastChatTime;
    private final AtomicLong count;
    private final AtomicLong subscriberCount;

    public ChatRoomAggregation(String streamId, Instant lastChatTime) {
        this.streamId = streamId;
        this.lastChatTime = new AtomicReference<>(lastChatTime);
        this.count = new AtomicLong(0);
        this.subscriberCount = new AtomicLong(0);
    }

    public void increaseCount(Instant eventTime, boolean isSubscriber) {
        count.incrementAndGet();
        if (isSubscriber) {
            subscriberCount.incrementAndGet();
        }
        lastChatTime.updateAndGet(current ->
            (current == null || eventTime.isAfter(current)) ? eventTime : current
        );
    }

    public String getStreamId() {
        return streamId;
    }

    public Instant getLastChatTime() {
        return lastChatTime.get();
    }

    public Long getCount() {
        return count.longValue();
    }

    public Long getSubscriberCount() {
        return subscriberCount.longValue();
    }
}
