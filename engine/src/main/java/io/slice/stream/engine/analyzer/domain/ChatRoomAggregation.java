package io.slice.stream.engine.analyzer.domain;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;

@Getter
public class ChatRoomAggregation {

    private final String streamId;
    private Instant lastChatTime;
    private AtomicLong count;

    public ChatRoomAggregation(String streamId, Instant lastChatTime) {
        this.streamId = streamId;
        this.lastChatTime = lastChatTime;
        this.count = new AtomicLong(0);
    }

    public Long getCount() {
        return count.longValue();
    }

    public void increaseCount(Instant eventTime) {
        count.incrementAndGet();
        if (this.lastChatTime == null || eventTime.isAfter(this.lastChatTime)) {
            this.lastChatTime = eventTime;
        }
    }
}
