package io.slice.stream.engine.chat.domain;

public interface ChatClient {

    void connect(String streamId, ChatMessageListener listener);

    void disconnect();
}
