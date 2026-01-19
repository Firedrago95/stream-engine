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
import java.util.concurrent.Executors;

public class ChzzkChatClient implements ChatClient {

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final ChzzkApiClient chzzkApiClient;

    private WebSocket webSocket;
    private ChatMessageListener listener;

    public ChzzkChatClient(ChzzkApiClient chzzkApiClient) {
        this.chzzkApiClient = chzzkApiClient;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
            .executor(this.executorService)
            .build();
    }

    @Override
    public void connect(String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        this.listener = listener;

        String accessToken = chzzkApiClient.getAccessToken(chatChannelId);

        URI uri = new URI("wss://kr-ss1.chat.naver.com/chat");

        ChzzkWebSocketListener webSocketListener = new ChzzkWebSocketListener(
            listener, chatChannelId, accessToken);

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
