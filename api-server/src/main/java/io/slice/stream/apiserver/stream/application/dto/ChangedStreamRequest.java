package io.slice.stream.apiserver.stream.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ChangedStreamRequest(
    @NotBlank String streamId,
    @NotBlank String liveId,
    String oldTitle,
    @NotBlank String newTitle,
    String oldCategory,
    String newCategory,
    @NotNull Instant changedAt,
    @NotNull Long changeOffsetMs,
    Boolean paidPromotion
) {

    public ChangedStreamRequest(
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
