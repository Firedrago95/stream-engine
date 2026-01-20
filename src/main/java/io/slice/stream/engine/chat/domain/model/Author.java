package io.slice.stream.engine.chat.domain.chatMessage;

public record Author(
    String id,
    String nickname,
    String profileImageUrl
) {
}
