package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;
import java.util.List;

public record SessionResponse(
    String sessionId,
    String title,
    String categoryName,
    Instant startedAt,
    Instant endedAt,
    Integer peakViewers,
    Integer averageViewerCount,
    Double subscriberChatRatio,
    boolean isAdult,
    List<String> linkedSessionIds
) {
    public SessionResponse(
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
        this(sessionId, title, categoryName, startedAt, endedAt, peakViewers, averageViewerCount, subscriberChatRatio, isAdult, List.of(sessionId));
    }

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
        this(sessionId, title, categoryName, startedAt, endedAt, peakViewers, averageViewerCount, subscriberChatRatio, false, List.of(sessionId));
    }

    public SessionResponse(String sessionId, Instant startedAt) {
        this(sessionId, null, null, startedAt, null, null, null, null, false, List.of(sessionId));
    }
}
