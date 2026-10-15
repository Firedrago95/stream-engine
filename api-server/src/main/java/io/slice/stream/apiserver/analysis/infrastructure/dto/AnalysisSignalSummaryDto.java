package io.slice.stream.apiserver.analysis.infrastructure.dto;

import java.time.Instant;

public record AnalysisSignalSummaryDto(
    String streamId,
    String sessionId,
    String status,
    long firepowerAvg,
    long firepowerMax,
    Instant timestampMinute,
    long offsetMs
) {}
