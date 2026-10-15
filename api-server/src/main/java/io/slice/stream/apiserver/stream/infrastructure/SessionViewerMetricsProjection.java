package io.slice.stream.apiserver.stream.infrastructure;

public interface SessionViewerMetricsProjection {

    String getSessionId();

    Double getAvgViewers();

    Integer getPeakViewers();
}
