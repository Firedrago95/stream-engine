package io.slice.stream.core.model;

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
    Instant startedAt,
    boolean adult,
    boolean paidPromotion
) {

    public StreamTarget(
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
        this(channelId, channelName, chatChannelId, liveId, liveTitle, concurrentUserCount, profileImageUrl, categoryName, startedAt, false, false);
    }

    public StreamTarget(
        String channelId,
        String channelName,
        String chatChannelId,
        long liveId,
        String liveTitle,
        int concurrentUserCount,
        String profileImageUrl,
        String categoryName,
        Instant startedAt,
        boolean adult
    ) {
        this(channelId, channelName, chatChannelId, liveId, liveTitle, concurrentUserCount, profileImageUrl, categoryName, startedAt, adult, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StreamTarget that = (StreamTarget) o;
        return liveId == that.liveId && Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, liveId);
    }

    public StreamTarget withChatChannelId(String newChatChannelId) {
        return new StreamTarget(
            channelId,
            channelName,
            newChatChannelId,
            liveId,
            liveTitle,
            concurrentUserCount,
            profileImageUrl,
            categoryName,
            startedAt,
            adult,
            paidPromotion
        );
    }
}
