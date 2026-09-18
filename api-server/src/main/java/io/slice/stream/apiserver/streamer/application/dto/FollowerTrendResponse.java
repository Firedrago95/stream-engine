package io.slice.stream.apiserver.streamer.application.dto;

import java.time.LocalDate;

public record FollowerTrendResponse(
    LocalDate date,
    int followerCount,
    int followerGrowth
) {
}
