package io.slice.stream.collector.domain;

import io.slice.stream.core.model.ChatMessage;
import java.util.List;

public interface ChatMessageListener {

    void onMessages(List<ChatMessage> messages);

    void onConnected();

    void onDisconnected();

    void onError(Throwable error);
}
