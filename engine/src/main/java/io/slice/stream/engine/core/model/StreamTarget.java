package io.slice.stream.engine.core.model;

import java.time.Instant;

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

}
