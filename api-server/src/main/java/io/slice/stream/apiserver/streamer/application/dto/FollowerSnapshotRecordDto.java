package io.slice.stream.apiserver.streamer.application.dto;

import java.time.LocalDate;

public record FollowerSnapshotRecordDto(
    String channelId,
    int followerCount,
    LocalDate snapshotDate
) {
}
