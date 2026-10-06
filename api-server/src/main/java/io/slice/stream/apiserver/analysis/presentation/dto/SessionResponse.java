package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;

public record SessionResponse(
    String sessionId,
    String title,
    String categoryName,
    Instant startedAt,
    Instant endedAt,
    Integer peakViewers,
    Integer averageViewerCount,
    Double subscriberChatRatio,
    boolean isAdult
) {
    public SessionResponse(
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt,
        Instant endedAt,
        Integer peakViewers,
        Integer averageViewerCount,
        Double subscriberChatRatio
    ) {
        this(sessionId, title, categoryName, startedAt, endedAt, peakViewers, averageViewerCount, subscriberChatRatio, false);
    }

    public SessionResponse(String sessionId, Instant startedAt) {
        this(sessionId, null, null, startedAt, null, null, null, null, false);
    }
}
