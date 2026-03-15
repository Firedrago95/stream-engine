package io.slice.stream.engine.analyzer.domain.aggregation;

import java.util.List;

public record ChatAggregationResult(
    String streamId,
    List<DataPoint> dataPoints
) {
    public record DataPoint(long timestamp, long value) {}
}
