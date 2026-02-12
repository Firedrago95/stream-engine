package io.slice.stream.apiserver.aggregation.domain;

import java.util.concurrent.atomic.AtomicLong;

public class ChatRoomAggregation {

    private final String streamId;
    private AtomicLong count;

    public ChatRoomAggregation(String streamId) {
        this.streamId = streamId;
        this.count = new AtomicLong(0);
    }

    public Long getCount() {
        return count.longValue();
    }

    public String getStreamId() {
        return streamId;
    }

    public void increaseCount() {
        count.addAndGet(1);
    }
}
