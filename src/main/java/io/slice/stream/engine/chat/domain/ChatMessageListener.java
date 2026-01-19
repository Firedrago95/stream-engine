package io.slice.stream.engine.chat.domain;

public interface ChatMessageListener {

    void onRawMessage(String rawMessage);

    void onConnected();

    void onDisconnected();

    void onError(Throwable throwable);
}
