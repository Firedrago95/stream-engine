package io.slice.stream.engine.analyzer.domain;

import java.util.List;

public record ChatAnalysisResult(
    String streamId,
    List<DataPoint> dataPoints
) {
    public record DataPoint(long timestamp, long value) {}
}
