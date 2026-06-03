package io.slice.stream.apiserver.stream.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record StreamSessionSummaryRequest(
    @NotNull @PositiveOrZero Double subscriberChatRatio,
    @NotNull String liveId,
    @NotNull Instant changedAt
) {}
