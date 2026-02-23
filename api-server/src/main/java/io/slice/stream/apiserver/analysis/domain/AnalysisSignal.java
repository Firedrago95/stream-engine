package io.slice.stream.apiserver.analysis.domain;

import java.time.Instant;

public record AnalysisSignal(
    String streamId,
    String status,
    Instant timestamp,
    long firepower
) {
    public static AnalysisSignal of (String streamId, String status, Instant timestamp, long firepower) {
        return new AnalysisSignal(streamId, status, timestamp, firepower);
    }
}
