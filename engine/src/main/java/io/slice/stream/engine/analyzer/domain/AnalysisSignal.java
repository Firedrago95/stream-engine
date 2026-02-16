package io.slice.stream.engine.analyzer.domain;

import java.time.Instant;

public record AnalysisSignal(
    String streamId,
    String status,
    Instant timestamp
) {

}
