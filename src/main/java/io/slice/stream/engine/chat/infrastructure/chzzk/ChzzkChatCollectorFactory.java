package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChzzkChatCollectorFactory implements ChatCollectorFactory {

    private final ChzzkApiClient chzzkApiClient;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final JsonMapper jsonMapper;

    @Override
    public ChatCollector start(String streamId) {
        ChatClient chzzkChatClient = new ChzzkChatClient(chzzkApiClient, httpClient, executorService, jsonMapper);
        ChatCollector chatCollector = new ChatCollector(chzzkChatClient, streamId, jsonMapper);
        chatCollector.start();
        return chatCollector;
    }
}
