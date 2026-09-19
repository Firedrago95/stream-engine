package io.slice.stream.core.model;

import java.time.Instant;
import java.util.Map;

public record ChatMessage(
    MessageType messageType,
    Author author,
    String message,
    Instant time,
    String streamId,
    long ingestedAt,
    Map<String, Object> headers
) {
}
