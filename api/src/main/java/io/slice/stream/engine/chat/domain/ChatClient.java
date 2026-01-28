package io.slice.stream.engine.chat.domain;

import java.net.URISyntaxException;

public interface ChatClient {

    void connect(String streamId, ChatMessageListener listener) throws URISyntaxException;

    void disconnect();
}
