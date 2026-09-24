package io.slice.stream.engine.analyzer.domain.aggregation;

public record ChatDelta(
    long totalCount,
    long subscriberCount
) {
    public boolean hasDelta() {
        return totalCount > 0;
    }
}
