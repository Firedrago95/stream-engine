package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    private final ChzzkApiClient chzzkApiClient;

    @Override
    public ChatCollector start(String streamId) {
        ChatClient chzzkChatClient = new ChzzkChatClient(chzzkApiClient);
        ChatCollector chatCollector = new ChatCollector(chzzkChatClient, streamId);
        chatCollector.start();
        return chatCollector;
    }
}
