package io.slice.stream.apiserver.aggregation.domain;

import java.util.List;

public record ChatAggregationResult(
    String streamId,
    List<DataPoint> dataPoints
) {
    public record DataPoint(long timestamp, long value) {}
}
