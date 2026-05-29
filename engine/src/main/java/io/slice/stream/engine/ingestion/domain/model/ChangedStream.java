package io.slice.stream.engine.ingestion.domain.model;

import java.time.Instant;

public record ChangedStream(
    String streamId,
    String oldTitle,
    String newTitle,
    String oldCategory,
    String newCategory,
    Instant changedAt,
    Long changedOffsetMs
) {

}
