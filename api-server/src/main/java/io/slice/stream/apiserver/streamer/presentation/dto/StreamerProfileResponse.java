package io.slice.stream.apiserver.streamer.presentation.dto;

import java.util.List;

public record StreamerProfileResponse(
    StreamerHeaderDto header,
    StreamerKpiSummaryDto summary,
    List<StreamerCategoryDto> mostPlayedCategories
) {
    public record StreamerHeaderDto(
        String channelId,
        String streamerName,
        String profileImageUrl,
        boolean isLive,
        int currentFollowers,
        int followerGrowth7d,
        int followerGrowth30d
    ) {}

    public record StreamerKpiSummaryDto(
        int averageViewers,
        int peakViewers,
        long totalBroadcastDurationSeconds,
        double hoursWatched,
        int followerGrowth30d,
        int broadcastDays30d,
        double attendanceRate30d
    ) {
        public StreamerKpiSummaryDto(
            int averageViewers,
            int peakViewers,
            long totalBroadcastDurationSeconds,
            double hoursWatched,
            int followerGrowth30d
        ) {
            this(averageViewers, peakViewers, totalBroadcastDurationSeconds, hoursWatched, followerGrowth30d, 0, 0.0);
        }
    }

    public record StreamerCategoryDto(
        String categoryName,
        long totalDurationSeconds,
        double percentage,
        int averageViewers
    ) {}
}
