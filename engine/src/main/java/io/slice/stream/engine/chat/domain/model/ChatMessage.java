package io.slice.stream.engine.chat.domain.model;

import java.time.Instant;
import java.util.Map;

public record ChatMessage(
    MessageType messageType,
    Author author,
    String message,
    Instant time,
    String streamId,
    Map<String, Object> headers
) {
}
