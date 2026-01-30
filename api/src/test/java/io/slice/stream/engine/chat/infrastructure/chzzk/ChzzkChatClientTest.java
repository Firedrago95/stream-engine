package io.slice.stream.engine.chat.infrastructure.chzzk;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.infrastructure.chzzk.api.ChzzkApiClient;
import io.slice.stream.engine.chat.infrastructure.chzzk.websocket.ChzzkWebSocketListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkChatClientTest {

    @Mock
    private ChzzkApiClient chzzkApiClient;

    @Mock
    private HttpClient httpClient;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private ChzzkMessageConverter messageConverter;

    @Mock
    private ChatMessageListener listener;

    @Mock
    private WebSocket.Builder webSocketBuilder;

    @Mock
    private WebSocket webSocket;

    private ChzzkChatClient chzzkChatClient;

    private static final String CHANNEL_ID = "testChannel";
    private static final String ACCESS_TOKEN = "testToken";

    @BeforeEach
    void setUp() {
        chzzkChatClient = new ChzzkChatClient(chzzkApiClient, httpClient, jsonMapper, messageConverter);
        lenient().when(httpClient.newWebSocketBuilder()).thenReturn(webSocketBuilder);
    }

    @Test
    void connect는_AccessToken을_받아_웹소켓_연결을_시도해야_한다() throws URISyntaxException {
        // given
        when(chzzkApiClient.getAccessToken(CHANNEL_ID)).thenReturn(ACCESS_TOKEN);
        when(webSocketBuilder.buildAsync(any(URI.class), any(ChzzkWebSocketListener.class)))
            .thenReturn(CompletableFuture.completedFuture(webSocket));

        // when
        chzzkChatClient.connect(CHANNEL_ID, listener);

        // then
        verify(chzzkApiClient).getAccessToken(CHANNEL_ID);
        verify(webSocketBuilder).buildAsync(any(URI.class), any(ChzzkWebSocketListener.class));
    }
    
    @Test
    void 웹소켓_연결이_실패하면_listener의_onError가_호출되어야_한다() throws URISyntaxException {
        // given
        RuntimeException testException = new RuntimeException("Connection failed");
        when(chzzkApiClient.getAccessToken(CHANNEL_ID)).thenReturn(ACCESS_TOKEN);
        when(webSocketBuilder.buildAsync(any(URI.class), any(ChzzkWebSocketListener.class)))
            .thenReturn(CompletableFuture.failedFuture(testException));

        // when
        chzzkChatClient.connect(CHANNEL_ID, listener);
        
        // then
        verify(listener).onError(testException);
    }

    @Test
    void disconnect는_활성화된_웹소켓의_sendClose를_호출해야_한다() throws URISyntaxException {
        // given
        when(chzzkApiClient.getAccessToken(CHANNEL_ID)).thenReturn(ACCESS_TOKEN);
        when(webSocketBuilder.buildAsync(any(URI.class), any(ChzzkWebSocketListener.class)))
            .thenReturn(CompletableFuture.completedFuture(webSocket));
        chzzkChatClient.connect(CHANNEL_ID, listener);

        // when
        chzzkChatClient.disconnect();

        // then
        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "클라이언트 연결 종료");
    }

    @Test
    void disconnect는_웹소켓이_없을_때_아무것도_하지_않아야_한다() {
        // when
        chzzkChatClient.disconnect();

        // then
        verify(webSocket, never()).sendClose(any(int.class), anyString());
    }
}
