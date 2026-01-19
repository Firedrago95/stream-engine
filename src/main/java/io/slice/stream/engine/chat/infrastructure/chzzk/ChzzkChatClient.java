package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.ChzzkWebSocketListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.ExecutorService;
import tools.jackson.databind.json.JsonMapper;

public class ChzzkChatClient implements ChatClient {

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final ChzzkApiClient chzzkApiClient;
    private final JsonMapper jsonMapper;

    private WebSocket webSocket;
    private ChatMessageListener listener;

    public ChzzkChatClient(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        ExecutorService executorService,
        JsonMapper jsonMapper
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.executorService = executorService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void connect(String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        this.listener = listener;

        String accessToken = chzzkApiClient.getAccessToken(chatChannelId);

        URI uri = new URI("wss://kr-ss1.chat.naver.com/chat");

        ChzzkWebSocketListener webSocketListener = new ChzzkWebSocketListener(
            listener, chatChannelId, accessToken, jsonMapper);
        
        httpClient.newWebSocketBuilder()
            .buildAsync(uri, webSocketListener)
            .thenAccept(ws -> this.webSocket = ws)
            .exceptionally(throwable -> {
                this.listener.onError(throwable);
                return null;
            });
    }

    @Override
    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnected");
            webSocket = null;
        }
    }
}
