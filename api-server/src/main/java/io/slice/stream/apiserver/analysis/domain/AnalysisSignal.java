package io.slice.stream.apiserver.analysis.domain;

import java.time.Instant;

public record AnalysisSignal(
    String streamId,
    String status,
    Instant timestamp
) {
    public static AnalysisSignal of (String streamId, String status, Instant timestamp) {
        return new AnalysisSignal(streamId, status, timestamp);
    }
}
