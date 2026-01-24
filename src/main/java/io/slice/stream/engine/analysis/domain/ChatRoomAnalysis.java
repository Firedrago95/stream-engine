package io.slice.stream.engine.analysis.domain;

import java.util.concurrent.atomic.AtomicLong;

public class ChatRoomAnalysis {

    private final String streamId;
    private AtomicLong count;

    public ChatRoomAnalysis(String streamId) {
        this.streamId = streamId;
        this.count = new AtomicLong(0);
    }

    public Long getCount() {
        return count.longValue();
    }

    public void increaseCount() {
        count.addAndGet(1);
    }
}
