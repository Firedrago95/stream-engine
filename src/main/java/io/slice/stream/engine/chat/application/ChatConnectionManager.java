package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatConnectionManager implements ChatCollector, ChatMessageListener {

    private final ChatClient chatClient;
    private final ChatMessageListener downstreamListener;
    private final String streamId;

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile boolean isManualDisconnect = false;
    private volatile int retryCount = 0;

    public ChatConnectionManager(ChatClient chatClient, ChatMessageListener downstreamListener, String streamId) {
        this.chatClient = chatClient;
        this.downstreamListener = downstreamListener;
        this.streamId = streamId;
    }

    @Override
    public void start() {
        this.isManualDisconnect = false;
        connect();
    }

    @Override
    public void disconnect() {
        isManualDisconnect = true;
        chatClient.disconnect();
        log.info("[{}] 수동으로 연결을 종료했습니다.", streamId);
    }

    private void connect() {
        if (isManualDisconnect) {
            log.info("[{}] 수동으로 연결이 종료되어, 재연결을 시도하지 않습니다.", streamId);
            return;
        }

        try {
            log.info("[{}] 채팅 채널 연결을 시도합니다.", streamId);
            chatClient.connect(streamId, this);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다.", streamId, e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isManualDisconnect) {
            return;
        }

        if (isReconnecting.compareAndSet(false, true)) {
            long delayMillis = calculateBackoffDelay();
            retryCount++;

            Thread.ofVirtual().name("reconnect-thread-" + streamId).start(() -> {
                try {
                    log.info("[{}] {}ms 후 재연결을 시도합니다. (시도 횟수: {})", streamId, delayMillis, retryCount);
                    Thread.sleep(delayMillis);
                    connect();
                } catch (InterruptedException e) {
                    log.warn("[{}] 재연결 대기 중 스레드가 중단되었습니다.", streamId);
                    Thread.currentThread().interrupt();
                } finally {
                    isReconnecting.set(false);
                }
            });
        }
    }

    private long calculateBackoffDelay() {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s, 30s...
        long delay = 1000L * (1L << Math.min(retryCount, 5));
        return Math.min(delay, 30000L);
    }

    @Override
    public void onMessages(List<ChatMessage> messages) {
        downstreamListener.onMessages(messages);
    }

    @Override
    public void onConnected() {
        log.info("[{}] 채팅 채널에 성공적으로 연결되었습니다.", streamId);
        retryCount = 0;
    }

    @Override
    public void onDisconnected() {
        log.warn("[{}] 채팅 채널 연결이 끊어졌습니다. 재연결을 시도합니다.", streamId);
        scheduleReconnect();
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("[{}] 채팅 채널에서 오류가 발생했습니다. 재연결을 시도합니다.", streamId, throwable);
        scheduleReconnect();
    }
}
