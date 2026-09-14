package io.slice.stream.apiserver.streamer.presentation.dto;

import java.time.Instant;
import java.util.List;

public record StreamerSessionHistoryResponse(
    List<StreamerSessionItemDto> sessions,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public record StreamerSessionItemDto(
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        int peakViewers,
        int avgViewers,
        Integer followerGrowth,
        Double subscriberChatRatio,
        String vodUrl
    ) {}
}
