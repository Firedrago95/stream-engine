package io.slice.stream.apiserver.stream.presentation.dto;

public record StreamItem(
    String streamId,
    String streamerName,
    String liveTitle,
    String thumbnailUrl,
    String categoryName
) {}
