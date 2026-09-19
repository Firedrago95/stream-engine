package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.ChatCollector;

public class FakeChatCollector implements ChatCollector {

    private final String channelId;
    private boolean connected = false;

    public FakeChatCollector(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public void start() {
        this.connected = true;
    }

    @Override
    public void disconnect() {
        this.connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getChannelId() {
        return channelId;
    }
}
