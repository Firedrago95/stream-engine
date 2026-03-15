package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;

public record HighlightResponse(
    Long id,
    String streamId,
    Instant startTime,
    Instant endTime,
    long durationSeconds,
    Long startTimeOffset,
    Long endTimeOffset,
    Long peakFirepower,
    String status
) {
    public static HighlightResponse from(HighlightEventEntity entity) {
        long duration = 0;
        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            duration = java.time.Duration.between(entity.getStartTime(), entity.getEndTime()).getSeconds();
        }

        return new HighlightResponse(
            entity.getId(),
            entity.getStreamId(),
            entity.getStartTime(),
            entity.getEndTime(),
            duration,
            entity.getStartTimeOffset(), // 매핑 추가
            entity.getEndTimeOffset(),   // 매핑 추가
            entity.getPeakFirepower(),
            entity.getStatus()
        );
    }
}
