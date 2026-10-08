package io.slice.stream.apiserver.admin.metrics;

public record AdminMetricsOverviewDto(
    String systemStatus,
    int targetChannelsCount,
    int activeAnalyzingCount,
    long liveStreamsCount,
    long todayHighlightCount,
    int hikariActiveConnections,
    int hikariPendingConnections,
    int hikariIdleConnections,
    int kafkaLag
) {
}
