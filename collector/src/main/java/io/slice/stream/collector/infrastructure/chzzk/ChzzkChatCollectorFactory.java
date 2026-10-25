package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.application.ChatConnectionManager;
import io.slice.stream.collector.domain.ChatClient;
import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatCollectorFactory;
import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.collector.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.collector.infrastructure.file.JsonlFileChatCollector;
import io.slice.stream.core.model.StreamTarget;
import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    private final ChzzkApiClient chzzkApiClient;
    private final HttpClient httpClient;
    private final ChzzkMessageConverter chzzkMessageConverter;
    private final JsonMapper jsonMapper;
    private final Executor executor;
    private final ScheduledExecutorService pingScheduler;
    private final String mockWebSocketUrl;
    private final String chatStorageDir;

    public ChzzkChatCollectorFactory(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        ChzzkMessageConverter chzzkMessageConverter,
        JsonMapper jsonMapper,
        Executor executor,
        ScheduledExecutorService pingScheduler,
        @Value("${chzzk.websocket.url:}") String mockWebSocketUrl,
        @Value("${collector.chat-storage.dir:data/chat-samples}") String chatStorageDir
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.chzzkMessageConverter = chzzkMessageConverter;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
        this.pingScheduler = pingScheduler;
        this.mockWebSocketUrl = mockWebSocketUrl;
        this.chatStorageDir = chatStorageDir;
    }

    @Override
    public ChatCollector start(StreamTarget streamTarget) {
        ChatClient chatClient = new ChzzkChatClient(
            chzzkApiClient, httpClient, jsonMapper, chzzkMessageConverter, pingScheduler, mockWebSocketUrl
        );

        ChatMessageListener messageListener = new JsonlFileChatCollector(
            streamTarget, chatStorageDir, jsonMapper
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
