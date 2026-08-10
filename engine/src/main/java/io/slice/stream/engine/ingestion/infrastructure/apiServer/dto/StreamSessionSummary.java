package io.slice.stream.engine.ingestion.infrastructure.apiServer.dto;

import java.time.Instant;

public record StreamSessionSummary(
    String streamId,
    String liveId,
    double subscriberChatRatio,
    Instant endedAt
) {}
