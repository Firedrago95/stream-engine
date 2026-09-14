package io.slice.stream.apiserver.streamer.domain.model;

import java.time.LocalDate;

public record DailySplitSegment(
    LocalDate date,
    long durationSeconds,
    int peakViewers,
    int avgViewers,
    String title,
    String category
) {
}
