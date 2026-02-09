package io.slice.stream.engine.chat.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatConnectionManagerTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatMessageListener downstreamListener;

    private ChatConnectionManager chatConnectionManager;

    private static final String STREAM_ID = "testStream";

    @BeforeEach
    void setUp() {
        chatConnectionManager = new ChatConnectionManager(chatClient, downstreamListener, STREAM_ID);
    }

    @Test
    void start_메서드는_chatClient의_connect를_호출해야_한다() throws URISyntaxException {
        // when
        chatConnectionManager.start();

        // then
        verify(chatClient).connect(STREAM_ID, chatConnectionManager);
    }

    @Test
    void disconnect_메서드는_chatClient의_disconnect를_호출하고_재연결을_막아야_한다() throws URISyntaxException {
        // when
        chatConnectionManager.disconnect();
        chatConnectionManager.onDisconnected();

        // then
        verify(chatClient).disconnect();
        verify(chatClient, never()).connect(anyString(), any(ChatMessageListener.class));
    }
    
    @Test
    void onMessages는_메시지를_downstreamListener로_전달해야_한다() {
        // given
        List<ChatMessage> messages = List.of(mock(ChatMessage.class));
        
        // when
        chatConnectionManager.onMessages(messages);
        
        // then
        verify(downstreamListener).onMessages(messages);
    }

    @Test
    void onDisconnected_이벤트_발생_시_재연결을_시도해야_한다() throws URISyntaxException {
        // given
        chatConnectionManager.start(); // initial connection
        
        // when
        chatConnectionManager.onDisconnected();

        // then
        verify(chatClient, timeout(2000).times(2)).connect(eq(STREAM_ID), any(ChatMessageListener.class));
    }
    
    @Test
    void onError_이벤트_발생_시_재연결을_시도해야_한다() throws URISyntaxException {
        // when
        chatConnectionManager.onError(new RuntimeException("Test error"));

        // then
        verify(chatClient, timeout(2000).times(1)).connect(eq(STREAM_ID), any(ChatMessageListener.class));
    }
    
    @Test
    void 최초_연결_실패_시_재연결을_시도해야_한다() throws URISyntaxException {
        // given
        doThrow(new RuntimeException("Connection failed")).when(chatClient).connect(anyString(), any(ChatMessageListener.class));

        // when
        chatConnectionManager.start();

        // then
        verify(chatClient, timeout(2000).times(2)).connect(eq(STREAM_ID), any(ChatMessageListener.class));
    }
    
    @Test
    void 수동으로_연결을_끊은_후에는_재연결을_시도하지_않아야_한다() throws URISyntaxException {
        // given
        chatConnectionManager.disconnect();

        // when
        chatConnectionManager.onDisconnected();

        // then
        verify(chatClient).disconnect();
        verify(chatClient, never()).connect(any(), any());
    }

    @Test
    void 연결_성공_시_재시도_횟수가_초기화되어야_하고_다음_재연결은_초기_딜레이로_시작해야_한다() throws URISyntaxException {
        // given
        doThrow(new RuntimeException("첫 번째 연결 실패"))
            .doNothing()
            .when(chatClient).connect(eq(STREAM_ID), any(ChatMessageListener.class));

        // when
        chatConnectionManager.start();
        verify(chatClient, timeout(2000).times(2)).connect(eq(STREAM_ID), any(ChatMessageListener.class));

        chatConnectionManager.onConnected();
        chatConnectionManager.onDisconnected();

        // then
        verify(chatClient, timeout(2000).times(3)).connect(eq(STREAM_ID), any(ChatMessageListener.class));
    }
}
