package io.slice.stream.apiserver.analysis.presentation.dto;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Duration;
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
        return from(entity, null);
    }

    public static HighlightResponse from(HighlightEventEntity entity, Instant baseStartedAt) {
        long duration = 0;
        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            duration = Duration.between(entity.getStartTime(), entity.getEndTime()).getSeconds();
        }

        Long startOffset = entity.getStartTimeOffset();
        Long endOffset = entity.getEndTimeOffset();

        if (baseStartedAt != null && entity.getStartTime() != null) {
            startOffset = Math.max(0L, Duration.between(baseStartedAt, entity.getStartTime()).toMillis());
            if (entity.getEndTime() != null) {
                endOffset = Duration.between(baseStartedAt, entity.getEndTime()).toMillis();
            }
        }

        return new HighlightResponse(
            entity.getId(),
            entity.getStreamId(),
            entity.getStartTime(),
            entity.getEndTime(),
            duration,
            startOffset,
            endOffset,
            entity.getPeakFirepower(),
            entity.getStatus()
        );
    }
}
