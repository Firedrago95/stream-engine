package io.slice.stream.engine.ingestion.infrastructure.apiServer.dto;

import java.time.LocalDate;

public record FollowerSnapshotRecord(
    String channelId,
    int followerCount,
    LocalDate snapshotDate
) {
}
