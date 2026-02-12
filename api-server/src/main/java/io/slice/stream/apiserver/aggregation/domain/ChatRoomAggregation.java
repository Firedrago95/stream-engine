package io.slice.stream.apiserver.aggregation.domain;

public class ChatRoomAggregation {

    private final String streamId;
    private long count;

    public ChatRoomAggregation(String streamId) {
        this.streamId = streamId;
        this.count = 0L;
    }

    public Long getCount() {
        return count;
    }

    public String getStreamId() {
        return streamId;
    }

    public void increaseCount() {
        count++;
    }
}
