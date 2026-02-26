package io.slice.stream.apiserver.stream.presentation.dto;

public record StreamResponse(
    String streamId,
    String streamerName,
    String liveTitle,
    String thumbnailUrl,
    String categoryName,
    String status
) {}
