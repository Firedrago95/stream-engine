package io.slice.stream.engine.ingestion.infrastructure.apiServer.dto;

import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;

public record StreamSyncRequest(
    String streamId,
    String liveId,
    String streamerName,
    String liveTitle,
    String profileImageUrl,
    int concurrentUserCount,
    String categoryName,
    Instant startedAt,
    boolean paidPromotion
) {
    public StreamSyncRequest(
        String streamId,
        String liveId,
        String streamerName,
        String liveTitle,
        String profileImageUrl,
        int concurrentUserCount,
        String categoryName,
        Instant startedAt
    ) {
        this(streamId, liveId, streamerName, liveTitle, profileImageUrl, concurrentUserCount, categoryName, startedAt, false);
    }

    public static StreamSyncRequest from(StreamTarget target) {
        return new StreamSyncRequest(
            target.channelId(),
            String.valueOf(target.liveId()),
            target.channelName(),
            target.liveTitle(),
            target.profileImageUrl(),
            target.concurrentUserCount(),
            target.categoryName(),
            target.startedAt(),
            target.paidPromotion()
        );
    }
}
