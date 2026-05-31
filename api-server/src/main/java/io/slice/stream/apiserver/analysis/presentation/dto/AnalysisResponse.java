package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
    String streamId,
    List<AnalysisDataPoint> dataPoints,
    List<SegmentResponse> segments
) {
    public AnalysisResponse(String streamId, List<AnalysisDataPoint> dataPoints) {
        this(streamId, dataPoints, List.of());
    }

    public record AnalysisDataPoint(
        long timestamp,
        long value,   // firepower
        String status,
        Long offsetMs
    ) {
        public AnalysisDataPoint(long timestamp, long value, String status) {
            this(timestamp, value, status, null);
        }
    }

    public record SegmentResponse(
        Long id,
        String title,
        String categoryName,
        Instant startedAt,
        Instant endedAt,
        Long startOffsetMs,
        Long endOffsetMs
    ) {}
}
