package io.slice.stream.apiserver.stream.presentation.dto;

import io.slice.stream.apiserver.stream.domain.StreamStatus;

public record StreamResponse(
    String streamId,
    String streamerName,
    String liveTitle,
    String profileImageUrl,
    String categoryName,
    int concurrentUserCount,
    StreamStatus status
) {}
