package io.slice.stream.apiserver.category.presentation.dto;

public record WeeklyCategoryResponse(
    int rank,
    String categoryName,
    String accumulatedViewHours,
    long exactHours,
    String change,
    Integer changeValue,
    String icon
) {
}
