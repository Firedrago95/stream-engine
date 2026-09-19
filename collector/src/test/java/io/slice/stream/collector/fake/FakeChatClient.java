package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.ChatClient;
import io.slice.stream.collector.domain.ChatMessageListener;
import java.net.URISyntaxException;

public class FakeChatClient implements ChatClient {

    private String connectedChannelId;
    private String connectedChatChannelId;
    private ChatMessageListener listener;
    private boolean connected = false;
    private boolean throwOnConnect = false;

    @Override
    public void connect(String channelId, String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        if (throwOnConnect) {
            throw new RuntimeException("강제 연결 에러");
        }
        this.connectedChannelId = channelId;
        this.connectedChatChannelId = chatChannelId;
        this.listener = listener;
        this.connected = true;
        listener.onConnected();
    }

    @Override
    public void disconnect() {
        this.connected = false;
    }

    public void triggerRemoteDisconnect() {
        this.connected = false;
        if (listener != null) {
            listener.onDisconnected();
        }
    }

    public void triggerRemoteError(Throwable error) {
        if (listener != null) {
            listener.onError(error);
        }
    }

    public void setThrowOnConnect(boolean throwOnConnect) {
        this.throwOnConnect = throwOnConnect;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getConnectedChannelId() {
        return connectedChannelId;
    }

    public String getConnectedChatChannelId() {
        return connectedChatChannelId;
    }
}
