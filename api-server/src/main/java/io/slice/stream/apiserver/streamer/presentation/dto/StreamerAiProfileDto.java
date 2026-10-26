package io.slice.stream.apiserver.streamer.presentation.dto;

import java.time.Instant;
import java.util.List;

public record StreamerAiProfileDto(
    String summaryLine,
    String broadcastingStyle,
    List<String> coreGames,
    String fandomTraits,
    List<String> popularMemes,
    int analyzedSessionCount,
    Instant updatedAt
) {}
