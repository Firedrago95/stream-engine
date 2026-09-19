package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.application.ChatConnectionManager;
import io.slice.stream.collector.domain.ChatClient;
import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatCollectorFactory;
import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.collector.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.collector.infrastructure.kafka.ChzzkChatCollector;
import io.slice.stream.core.model.ChatMessage;
import io.slice.stream.core.model.StreamTarget;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
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
    private final Executor executor;
    private final String mockWebSocketUrl;
    private final String kafkaTopic;

    public ChzzkChatCollectorFactory(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        ChzzkMessageConverter chzzkMessageConverter,
        JsonMapper jsonMapper,
        KafkaTemplate<String, ChatMessage> kafkaTemplate,
        Executor executor,
        @Value("${chzzk.websocket.url:}") String mockWebSocketUrl,
        @Value("${kafka.topic.chat:chat-messages}") String kafkaTopic
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.chzzkMessageConverter = chzzkMessageConverter;
        this.jsonMapper = jsonMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.executor = executor;
        this.mockWebSocketUrl = mockWebSocketUrl;
        this.kafkaTopic = kafkaTopic;
    }

    @Override
    public ChatCollector start(StreamTarget streamTarget) {
        ChatClient chatClient = new ChzzkChatClient(
            chzzkApiClient, httpClient, jsonMapper, chzzkMessageConverter, executor, mockWebSocketUrl
        );

        ChatMessageListener messageListener = new ChzzkChatCollector(
            streamTarget.channelId(), kafkaTemplate, kafkaTopic
        );

        ChatCollector connectionManager = new ChatConnectionManager(
            chatClient,
            messageListener,
            streamTarget.chatChannelId(),
            streamTarget.channelId(),
            executor
        );

        connectionManager.start();
        return connectionManager;
    }
}
