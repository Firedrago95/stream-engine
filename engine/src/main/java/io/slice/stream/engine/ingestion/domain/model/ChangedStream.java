package io.slice.stream.engine.ingestion.domain.model;

public record ChangedStream(
    String streamId,
    String oldTitle,
    String newTitle,
    String oldCategory,
    String newCategory
) {

}
