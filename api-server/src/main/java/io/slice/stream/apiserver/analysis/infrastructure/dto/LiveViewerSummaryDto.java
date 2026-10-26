package io.slice.stream.apiserver.analysis.infrastructure.dto;

import java.time.Instant;

public record LiveViewerSummaryDto(
    String streamId,
    String sessionId,
    Instant timestampMinute,
    long offsetMs,
    int viewerCount
) {}
