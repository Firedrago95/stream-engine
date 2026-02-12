package io.slice.stream.apiserver.analysis.domain;

import java.util.List;

public record ChatAnalysisResult(
    String streamId,
    List<DataPoint> dataPoints
) {
    public record DataPoint(long timestamp, long value) {}
}
