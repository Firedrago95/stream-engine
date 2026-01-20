package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.application.ChatConnectionManager;
import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.chatMessage.ChatMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import java.net.http.HttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    private final ChzzkApiClient chzzkApiClient;
    private final HttpClient httpClient;
    private final ChzzkMessageConverter chzzkMessageConverter;
    private final JsonMapper jsonMapper;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    @Override
    public ChatCollector start(String streamId) {
        ChatClient chzzkChatClient = new ChzzkChatClient(chzzkApiClient, httpClient, jsonMapper, chzzkMessageConverter);
        ChatMessageListener messageListener = new ChzzkChatCollector(streamId, kafkaTemplate);
        ChatCollector connectionManager = new ChatConnectionManager(chzzkChatClient, messageListener, streamId);

        connectionManager.start();
        return connectionManager;
    }
}
