package io.slice.stream.apiserver.streamer.presentation.dto;

import java.time.Instant;
import java.util.List;

public record StreamerCalendarResponse(
    String channelId,
    int year,
    int month,
    List<CalendarSessionDto> sessions
) {

    public record CalendarSessionDto(
        String sessionId,
        String title,
        String categoryName,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        int peakViewers,
        int averageViewers,
        boolean isLive
    ) {}
}
