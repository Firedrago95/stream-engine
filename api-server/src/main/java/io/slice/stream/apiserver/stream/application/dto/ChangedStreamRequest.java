package io.slice.stream.apiserver.stream.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ChangedStreamRequest(
    @NotBlank String streamId,
    String oldTitle,
    @NotBlank String newTitle,
    String oldCategory,
    @NotBlank String newCategory,
    @NotNull Instant changedAt,
    @NotNull Long changeOffsetMs
) {

}
