package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.application.ChatConnectionManager;
import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.engine.chat.infrastructure.kafka.ChzzkChatCollector;
import io.slice.stream.engine.core.model.StreamTarget;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    private final ChzzkApiClient chzzkApiClient;
    private final HttpClient httpClient;
    private final ChzzkMessageConverter chzzkMessageConverter;
    private final JsonMapper jsonMapper;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;
    private final ExecutorService executorService;
    private final String mockWebSocketUrl;

    public ChzzkChatCollectorFactory(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        ChzzkMessageConverter chzzkMessageConverter,
        JsonMapper jsonMapper,
        KafkaTemplate<String, ChatMessage> kafkaTemplate,
        ExecutorService virtualThreadExecutor,
        @Value("${chzzk.websocket.url:}") String mockWebSocketUrl
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.chzzkMessageConverter = chzzkMessageConverter;
        this.jsonMapper = jsonMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.executorService = virtualThreadExecutor;
        this.mockWebSocketUrl = mockWebSocketUrl;
    }

    @Override
    public ChatCollector start(StreamTarget streamTarget) {
        ChatClient chzzkChatClient = new ChzzkChatClient(
            chzzkApiClient,
            httpClient,
            jsonMapper,
            chzzkMessageConverter,
            executorService,
            mockWebSocketUrl
        );
        ChatMessageListener messageListener = new ChzzkChatCollector(streamTarget.channelId(), kafkaTemplate);

        ChatCollector connectionManager = new ChatConnectionManager(
            chzzkChatClient,
            messageListener,
            streamTarget.chatChannelId(),
            streamTarget.channelId(),
            executorService
        );

        connectionManager.start();
        return connectionManager;
    }
}
