package io.slice.stream.apiserver.category.domain;

public record CategoryViewMetric(
    String categoryName,
    long exactHours
) {
}
