package io.slice.stream.engine.analyzer.domain.signal;

import java.time.Instant;

public record AnalysisSignal(
    String streamId,
    String liveId,
    String status,
    Instant timestamp,
    long firepower,
    Long offsetMs
) {

}
