package io.slice.stream.engine.chat.infrastructure.chzzk;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.ChzzkWebSocketListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class MockChatClient implements ChatClient {

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final ChzzkMessageConverter messageConverter;
    private final ExecutorService executorService;
    private final String mockUrl;
    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    private ChatMessageListener listener;

    public MockChatClient(HttpClient httpClient, JsonMapper jsonMapper, ChzzkMessageConverter messageConverter, ExecutorService executorService, String mockUrl) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.messageConverter = messageConverter;
        this.executorService = executorService;
        this.mockUrl = mockUrl;
    }

    @Override
    public void connect(String channelId, String chatChannelId, ChatMessageListener listener) throws URISyntaxException {
        this.listener = listener;
        log.info("[테스트 모드] {} 방을 로컬 목 서버({})로 연결합니다.", chatChannelId, mockUrl);

        // API 호출 없이 더미 토큰 사용
        String dummyToken = "dummy_token_for_load_test";
        URI uri = new URI(mockUrl);

        ChzzkWebSocketListener webSocketListener = new ChzzkWebSocketListener(
            listener, channelId, chatChannelId, dummyToken, jsonMapper, messageConverter, executorService
        );

        httpClient.newWebSocketBuilder()
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
