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
    boolean paidPromotion,
    boolean adult
) {
    public StreamSyncRequest(
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
        this(streamId, liveId, streamerName, liveTitle, profileImageUrl, concurrentUserCount, categoryName, startedAt, paidPromotion, false);
    }

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
        this(streamId, liveId, streamerName, liveTitle, profileImageUrl, concurrentUserCount, categoryName, startedAt, false, false);
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
            Boolean.TRUE.equals(target.paidPromotion()),
            Boolean.TRUE.equals(target.adult())
        );
    }
}
