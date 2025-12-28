package io.slice.stream.engine.core.model;

public record StreamTarget(
        String channelId,
        String channelName,
        String liveTitle,
        int concurrentUserCount
) {
}
