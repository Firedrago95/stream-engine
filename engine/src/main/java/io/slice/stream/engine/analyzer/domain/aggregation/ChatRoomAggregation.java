package io.slice.stream.engine.analyzer.domain.aggregation;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ChatRoomAggregation {

    private final String streamId;
    private final AtomicReference<Instant> lastChatTime;
    private final AtomicLong count;

    public ChatRoomAggregation(String streamId, Instant lastChatTime) {
        this.streamId = streamId;
        this.lastChatTime = new AtomicReference<>(lastChatTime);
        this.count = new AtomicLong(0);
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

    public void increaseCount(Instant eventTime) {
        count.incrementAndGet();
        lastChatTime.updateAndGet(current ->
            (current == null || eventTime.isAfter(current)) ? eventTime : current
        );
    }
}
