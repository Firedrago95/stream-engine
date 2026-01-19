package io.slice.stream.engine.chat.domain;

import tools.jackson.databind.JsonNode;

public interface ChatMessageListener {

    void onMessage(JsonNode jsonNode);

    void onConnected();

    void onDisconnected();

    void onError(Throwable throwable);
}
