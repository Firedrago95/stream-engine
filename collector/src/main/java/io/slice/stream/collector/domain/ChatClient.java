package io.slice.stream.collector.domain;

import java.net.URISyntaxException;

public interface ChatClient {

    void connect(String channelId, String chatChannelId, ChatMessageListener listener) throws URISyntaxException;

    void disconnect();
}
