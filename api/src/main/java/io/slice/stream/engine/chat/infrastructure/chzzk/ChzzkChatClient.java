package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.ChzzkWebSocketListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import tools.jackson.databind.json.JsonMapper;

public class ChzzkChatClient implements ChatClient {

    private final HttpClient httpClient;
    private final ChzzkApiClient chzzkApiClient;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private ChatMessageListener listener;

    public ChzzkChatClient(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.messageConverter = messageConverter;
    }

    @Override
    public void connect(String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        this.listener = listener;

        String accessToken = chzzkApiClient.getAccessToken(chatChannelId);

        URI uri = new URI("wss://kr-ss1.chat.naver.com/chat");

        ChzzkWebSocketListener webSocketListener = new ChzzkWebSocketListener(
            listener,
            chatChannelId,
            accessToken,
            jsonMapper,
            messageConverter
        );

        httpClient.newWebSocketBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://chzzk.naver.com")
            .header("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .buildAsync(uri, webSocketListener)
            .thenAccept(webSocketRef::set)
            .exceptionally(throwable -> {
                Throwable cause = (throwable instanceof CompletionException) ? throwable.getCause() : throwable;
                this.listener.onError(cause);
                return null;
            });
    }

    @Override
    public void disconnect() {
        WebSocket ws = webSocketRef.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "클라이언트 연결 종료");
        }
    }
}
