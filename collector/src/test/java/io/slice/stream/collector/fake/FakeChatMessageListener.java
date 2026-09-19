package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.core.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class FakeChatMessageListener implements ChatMessageListener {

    private final List<ChatMessage> receivedMessages = new ArrayList<>();
    private boolean connected = false;
    private boolean disconnected = false;
    private Throwable error = null;

    @Override
    public void onMessages(List<ChatMessage> messages) {
        receivedMessages.addAll(messages);
    }

    @Override
    public void onConnected() {
        this.connected = true;
    }

    @Override
    public void onDisconnected() {
        this.disconnected = true;
    }

    @Override
    public void onError(Throwable error) {
        this.error = error;
    }

    public List<ChatMessage> getReceivedMessages() {
        return receivedMessages;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    public Throwable getError() {
        return error;
    }
}
