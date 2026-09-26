package io.slice.stream.engine.ingestion.domain.model;

import java.time.Instant;

public record ChangedStream(
    String streamId,
    String liveId,
    String oldTitle,
    String newTitle,
    String oldCategory,
    String newCategory,
    Instant changedAt,
    Long changeOffsetMs,
    Boolean paidPromotion
) {

    public ChangedStream(
        String streamId,
        String liveId,
        String oldTitle,
        String newTitle,
        String oldCategory,
        String newCategory,
        Instant changedAt,
        Long changeOffsetMs
    ) {
        this(streamId, liveId, oldTitle, newTitle, oldCategory, newCategory, changedAt, changeOffsetMs, false);
    }
}
