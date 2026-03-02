package io.slice.stream.engine.chat.domain;

import java.net.URISyntaxException;

public interface ChatClient {

    void connect(String channelId, String chatChannelId, ChatMessageListener listener) throws URISyntaxException;

    void disconnect();
}
