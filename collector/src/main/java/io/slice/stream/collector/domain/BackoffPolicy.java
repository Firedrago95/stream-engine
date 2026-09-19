package io.slice.stream.collector.domain;

@FunctionalInterface
public interface BackoffPolicy {

    long calculateDelay(int retryCount);

    static BackoffPolicy exponential(long baseDelayMs, long maxDelayMs) {
        return retryCount -> {
            long delay = baseDelayMs * (1L << Math.min(retryCount, 5));
            return Math.min(delay, maxDelayMs);
        };
    }

    static BackoffPolicy noDelay() {
        return retryCount -> 0L;
    }
}
