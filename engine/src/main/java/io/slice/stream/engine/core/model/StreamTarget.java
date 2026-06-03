package io.slice.stream.engine.core.model;

import java.time.Instant;
import java.util.Objects;

public record StreamTarget(
    String channelId,
    String channelName,
    String chatChannelId,
    long liveId,
    String liveTitle,
    int concurrentUserCount,
    String profileImageUrl,
    String categoryName,
    Instant startedAt
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StreamTarget that = (StreamTarget) o;
        return liveId == that.liveId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(liveId);
    }
}
