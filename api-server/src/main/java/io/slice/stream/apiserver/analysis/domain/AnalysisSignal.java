package io.slice.stream.apiserver.analysis.domain;

import java.time.Instant;

public record AnalysisSignal(
    String streamId,
    String sessionId,
    String status,
    Instant timestamp,
    long firepower,
    Long offsetMs
) {
    public static AnalysisSignal of(String streamId, String sessionId, String status, Instant timestamp, long firepower, Long offsetMs) {
        return new AnalysisSignal(streamId, sessionId, status, timestamp, firepower, offsetMs);
    }
}
