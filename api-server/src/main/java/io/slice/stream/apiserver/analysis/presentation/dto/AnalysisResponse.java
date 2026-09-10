package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
    String streamId,
    List<AnalysisDataPoint> dataPoints,
    List<SegmentResponse> segments,
    List<TimelineDataPoint> timeline,
    SessionSummaryResponse summary
) {
    public AnalysisResponse(String streamId, List<AnalysisDataPoint> dataPoints) {
        this(streamId, dataPoints, List.of(), List.of(), null);
    }

    public AnalysisResponse(String streamId, List<AnalysisDataPoint> dataPoints, List<SegmentResponse> segments) {
        this(streamId, dataPoints, segments, List.of(), null);
    }

    public record AnalysisDataPoint(
        long timestamp,
        long value,
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

    public record TimelineDataPoint(
        long timestamp,
        int viewerCount
    ) {}

    public record SessionSummaryResponse(
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt,
        Instant endedAt,
        Integer peakViewers,
        Integer averageViewerCount,
        Double subscriberChatRatio
    ) {}
}
