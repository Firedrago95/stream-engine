package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.domain.ChatClient;
import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.collector.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.collector.infrastructure.chzzk.websocket.ChzzkWebSocketListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class ChzzkChatClient implements ChatClient {

    private static final String DEFAULT_WS_URL = "wss://kr-ss1.chat.naver.com/chat";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ChzzkApiClient chzzkApiClient;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private final Executor executor;
    private final String webSocketUrl;

    public ChzzkChatClient(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter,
        Executor executor
    ) {
        this(chzzkApiClient, httpClient, jsonMapper, messageConverter, executor, DEFAULT_WS_URL);
    }

    public ChzzkChatClient(
        ChzzkApiClient chzzkApiClient,
        HttpClient httpClient,
        JsonMapper jsonMapper,
        ChzzkMessageConverter messageConverter,
        Executor executor,
        String webSocketUrl
    ) {
        this.chzzkApiClient = chzzkApiClient;
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.messageConverter = messageConverter;
        this.executor = executor;
        this.webSocketUrl = webSocketUrl != null && !webSocketUrl.isBlank() ? webSocketUrl : DEFAULT_WS_URL;
    }

    @Override
    public void connect(String channelId, String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        String accessToken = chzzkApiClient.getAccessToken(chatChannelId);
        if (accessToken == null) {
            listener.onError(new IllegalStateException("치지직 accessToken 발급에 실패했습니다. chatChannelId: " + chatChannelId));
            return;
        }

        URI uri = new URI(webSocketUrl);
        ChzzkWebSocketListener webSocketListener = new ChzzkWebSocketListener(
            listener, channelId, chatChannelId, accessToken, jsonMapper, messageConverter, executor
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
                listener.onError(cause);
                return null;
            });
    }

    @Override
    public void disconnect() {
        WebSocket ws = webSocketRef.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "수집기 정상 종료");
        }
    }
}
