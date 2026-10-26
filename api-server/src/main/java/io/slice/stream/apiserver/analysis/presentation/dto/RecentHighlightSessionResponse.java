package io.slice.stream.apiserver.analysis.presentation.dto;

import java.time.Instant;

public record RecentHighlightSessionResponse(
    String streamId,
    String streamerName,
    String sessionId,
    String title,
    String categoryName,
    Instant startedAt,
    Instant endedAt,
    long durationSeconds,
    int peakViewers,
    int avgViewers,
    long highlightCount
) {
}
