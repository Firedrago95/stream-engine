package io.slice.stream.apiserver.stream.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record StreamSyncRequest(
    @NotBlank(message = "채널 ID는 필수입니다.")
    String streamId,

    @NotBlank(message = "스트리머 이름은 필수입니다.")
    String streamerName,

    @NotBlank(message = "방송 제목은 필수입니다.")
    String liveTitle,
    String profileImageUrl,
    String categoryName
) {}
