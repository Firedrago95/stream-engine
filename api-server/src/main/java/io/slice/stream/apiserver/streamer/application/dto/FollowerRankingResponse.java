package io.slice.stream.apiserver.streamer.application.dto;

import io.slice.stream.apiserver.stream.domain.StreamStatus;

public record FollowerRankingResponse(
    String streamId,
    String streamerName,
    String liveTitle,
    String profileImageUrl,
    String categoryName,
    StreamStatus status,
    int concurrentUserCount,
    int followerCount,
    int weeklyGrowth
) {
}
