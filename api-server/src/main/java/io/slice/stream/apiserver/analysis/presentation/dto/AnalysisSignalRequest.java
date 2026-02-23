package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import java.time.Instant;

public record AnalysisSignalRequest(
    String streamId,
    String status,
    Instant timestamp
) {
    public AnalysisSignal toDomain() {
        return AnalysisSignal.of(streamId, status, timestamp);
    }
}
