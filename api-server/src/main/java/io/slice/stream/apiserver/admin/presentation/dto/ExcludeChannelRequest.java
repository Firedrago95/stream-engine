package io.slice.stream.apiserver.admin.presentation.dto;

public record ExcludeChannelRequest(
    String channelId,
    String streamerName,
    String reason,
    Integer durationDays
) {
}
