package io.slice.stream.apiserver.stream.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StreamSessionSummaryRequest(
    @NotNull
    @PositiveOrZero
    Double subscriberChatRatio
) {}
