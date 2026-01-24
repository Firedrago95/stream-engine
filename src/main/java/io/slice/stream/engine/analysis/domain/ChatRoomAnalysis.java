package io.slice.stream.engine.analysis.domain;

import java.util.concurrent.atomic.AtomicLong;

public class ChatRoomAnalysis {

    private AtomicLong count;

    public ChatRoomAnalysis() {
        count = new AtomicLong(1);
    }

    public Long getCount() {
        return count.longValue();
    }

    public void increaseCount() {
        count.addAndGet(1);
    }
}
