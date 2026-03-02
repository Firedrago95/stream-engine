package io.slice.stream.engine.ingestion.infrastructure.apiServer.dto;

import io.slice.stream.engine.core.model.StreamTarget;

public record StreamSyncRequest(
    String streamId,
    String streamerName,
    String liveTitle,
    String profileImageUrl,
    String categoryName
) {
    public static StreamSyncRequest from(StreamTarget target) {
        return new StreamSyncRequest(
            target.channelId(),
            target.channelName(),
            target.liveTitle(),
            target.profileImageUrl(),
            target.categoryName()
        );
    }
}
