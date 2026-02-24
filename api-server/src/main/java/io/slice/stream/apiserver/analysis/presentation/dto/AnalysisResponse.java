package io.slice.stream.apiserver.analysis.presentation.dto;

import java.util.List;

public record AnalysisResponse(
    String streamId,
    List<AnalysisDataPoint> dataPoints
) {
    public record AnalysisDataPoint(
        long timestamp,
        long value,
        String status
    ) {}
}
