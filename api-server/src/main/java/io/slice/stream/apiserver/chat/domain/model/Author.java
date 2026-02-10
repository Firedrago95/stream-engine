package io.slice.stream.apiserver.chat.domain.model;

public record Author(
    String id,
    String nickname,
    String profileImageUrl
) {
}
