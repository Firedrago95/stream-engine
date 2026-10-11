package io.slice.stream.collector.infrastructure.chzzk.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.collector.infrastructure.chzzk.ChzzkMessageConverter;
import java.net.http.WebSocket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ChzzkWebSocketListenerTest {

    private ChatMessageListener messageListener;
    private ScheduledExecutorService pingScheduler;
    private ScheduledFuture<?> scheduledFuture;
    private WebSocket webSocket;
    private JsonMapper jsonMapper;
    private ChzzkMessageConverter messageConverter;
    private ChzzkWebSocketListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        messageListener = mock(ChatMessageListener.class);
        pingScheduler = mock(ScheduledExecutorService.class);
        scheduledFuture = mock(ScheduledFuture.class);
        webSocket = mock(WebSocket.class);
        jsonMapper = JsonMapper.builder().build();
        messageConverter = new ChzzkMessageConverter(jsonMapper);

        when(pingScheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> scheduledFuture);

        listener = new ChzzkWebSocketListener(
            messageListener,
            "channel_123",
            "chat_123",
            "access_token_abc",
            jsonMapper,
            messageConverter,
            pingScheduler
        );
    }

    @Test
    @DisplayName("웹소켓 연결 시 20초 주기의 핑 전송 태스크가 스케줄러에 등록된다")
    void schedulePingOnOpen() {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        listener.onOpen(webSocket);

        verify(pingScheduler, times(1)).scheduleAtFixedRate(
            runnableCaptor.capture(),
            eq(20L),
            eq(20L),
            eq(TimeUnit.SECONDS)
        );

        Runnable pingTask = runnableCaptor.getValue();
        pingTask.run();

        verify(webSocket, times(1)).sendText("{\"cmd\": 0, \"ver\": 2}", true);
    }

    @Test
    @DisplayName("웹소켓 연결 종료 시 등록된 핑 스케줄러 태스크가 취소된다")
    void cancelPingOnClose() {
        listener.onOpen(webSocket);
        listener.onClose(webSocket, 1000, "정상 종료");

        verify(scheduledFuture, times(1)).cancel(true);
        verify(messageListener, times(1)).onDisconnected();
    }

    @Test
    @DisplayName("웹소켓 에러 발생 시 등록된 핑 스케줄러 태스크가 취소된다")
    void cancelPingOnError() {
        listener.onOpen(webSocket);
        listener.onError(webSocket, new RuntimeException("네트워크 단절"));

        verify(scheduledFuture, times(1)).cancel(true);
        verify(messageListener, times(1)).onError(any(Throwable.class));
    }
}
