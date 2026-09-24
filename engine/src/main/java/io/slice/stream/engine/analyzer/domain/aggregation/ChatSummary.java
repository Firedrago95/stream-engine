package io.slice.stream.engine.analyzer.domain.aggregation;

public record ChatSummary(
    long totalChatCount,
    long subscriberChatCount
) {
    public double calculateSubscriberRatio() {
        if (totalChatCount == 0) {
            return 0.0;
        }
        return Math.round(((double) subscriberChatCount / totalChatCount) * 1000.0) / 10.0;
    }
}
