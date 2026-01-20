package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
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
        ChatClient chzzkChatClient = new ChzzkChatClient(chzzkApiClient, httpClient, jsonMapper);
        ChatCollector chatCollector = new ChzzkChatCollector(chzzkChatClient, streamId, chzzkMessageConverter, kafkaTemplate);
        chatCollector.start();
        return chatCollector;
    }
}
