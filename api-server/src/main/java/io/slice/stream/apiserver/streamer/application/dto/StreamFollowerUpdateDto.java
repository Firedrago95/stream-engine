package io.slice.stream.apiserver.streamer.application.dto;

import java.time.Instant;

public record StreamFollowerUpdateDto(
    String streamId,
    int followerCount,
    Instant updatedAt
) {
}
