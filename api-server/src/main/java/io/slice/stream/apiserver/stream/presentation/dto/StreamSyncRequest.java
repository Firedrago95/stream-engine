package io.slice.stream.apiserver.stream.presentation.dto;

public record StreamSyncRequest(
    String channelId,
    String channelName,
    String liveTitle,
    String thumbnailUrl,
    String categoryName
) {}
