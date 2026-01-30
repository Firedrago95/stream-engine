package io.slice.stream.engine.core.model;

public record StreamTarget(
    String channelId,
    String channelName,
    String chatChannelId,
    long liveId,
    String liveTitle,
    int concurrentUserCount
) {

}
