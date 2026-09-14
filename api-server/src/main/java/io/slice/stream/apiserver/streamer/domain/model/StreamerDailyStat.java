package io.slice.stream.apiserver.streamer.domain.model;

import java.time.LocalDate;

public record StreamerDailyStat(
    Long id,
    String channelId,
    LocalDate statDate,
    long broadcastDurationSeconds,
    int averageViewers,
    int peakViewers,
    double hoursWatched,
    Integer followerCount,
    Integer followerGrowth,
    String representativeTitle,
    String dominantCategory,
    int sessionCount
) {
    public static StreamerDailyStat of(
        String channelId,
        LocalDate statDate,
        long broadcastDurationSeconds,
        int averageViewers,
        int peakViewers,
        double hoursWatched,
        Integer followerCount,
        Integer followerGrowth,
        String representativeTitle,
        String dominantCategory,
        int sessionCount
    ) {
        return new StreamerDailyStat(
            null,
            channelId,
            statDate,
            broadcastDurationSeconds,
            averageViewers,
            peakViewers,
            hoursWatched,
            followerCount,
            followerGrowth,
            representativeTitle,
            dominantCategory,
            sessionCount
        );
    }
}
