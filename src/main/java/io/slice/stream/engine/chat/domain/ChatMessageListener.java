package io.slice.stream.engine.chat.domain;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.List;

public interface ChatMessageListener {

    void onMessages(List<ChatMessage> messages);

    void onConnected();

    void onDisconnected();

    void onError(Throwable throwable);
}
