package io.slice.stream.core.model;

public record Author(
    String id,
    String nickname,
    String profileImageUrl,
    boolean isSubscriber
) {
}
