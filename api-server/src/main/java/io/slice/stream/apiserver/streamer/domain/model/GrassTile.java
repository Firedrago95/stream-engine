package io.slice.stream.apiserver.streamer.domain.model;

import java.time.LocalDate;

public record GrassTile(
    LocalDate date,
    GrassLevel level,
    long durationSeconds,
    int avgViewers,
    int peakViewers,
    String representativeTitle,
    String dominantCategory
) {
    public static GrassTile empty(LocalDate date) {
        return new GrassTile(date, GrassLevel.LEVEL_0, 0L, 0, 0, null, null);
    }
}
