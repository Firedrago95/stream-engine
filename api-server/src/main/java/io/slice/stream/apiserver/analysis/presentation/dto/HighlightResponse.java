package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;

public record HighlightResponse(
    Long id,
    String streamId,
    String status,
    Instant startTime,
    Instant endTime,
    Long peakFirepower,
    Long durationSeconds
) {
    public static HighlightResponse from(HighlightEventEntity entity) {
        return new HighlightResponse(
            entity.getId(),
            entity.getStreamId(),
            entity.getStatus(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getPeakFirepower(),
            entity.getEndTime() != null ?
                java.time.Duration.between(entity.getStartTime(), entity.getEndTime()).toSeconds() : 0L
        );
    }
}
