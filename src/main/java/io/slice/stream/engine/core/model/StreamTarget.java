package io.slice.stream.engine.core.model;

public record StreamTarget(
        long liveId,
        String liveTitle,
        String channelId,
        String channelName,
        int concurrentUserCount
) {
}
