package io.slice.stream.engine.chat.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

public record ChatMessage(
    MessageType messageType,
    Author author,
    String message,
    LocalDateTime time,
    Map<String, Object> headers
) {
    public boolean hasHeader(String key) {
        return headers != null && headers.containsKey(key);
    }
}
