package io.slice.stream.apiserver.streamer.presentation.dto;

import io.slice.stream.apiserver.streamer.domain.model.GrassTile;
import java.util.List;

public record StreamerGrassResponse(
    String channelId,
    int currentStreak,
    long totalBroadcastDurationSeconds,
    int totalBroadcastDays,
    List<GrassTile> tiles
) {
}
