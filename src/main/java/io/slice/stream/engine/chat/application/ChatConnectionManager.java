package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatConnectionManager implements ChatCollector {

    private final ChatClient chatClient;
    private final ChatMessageListener messageListener;
    private final String streamId;

    private volatile boolean isManualDisconnect = false;
    private int retryCount = 0;

    public ChatConnectionManager(ChatClient chatClient, ChatMessageListener messageListener, String streamId) {
        this.chatClient = chatClient;
        this.messageListener = messageListener;
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
    }

    private void connect() {
        if (isManualDisconnect) {
            log.info("[{}] 수동으로 연결이 종료되어, 재연결을 시도하지 않습니다.", streamId);
            return;
        }

        try {
            chatClient.connect(streamId, messageListener);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다.", streamId, e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isManualDisconnect) return;

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
            }
        });
    }

    private long calculateBackoffDelay() {
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, max 30s
        long delay = 1000L * (1L << Math.min(retryCount, 5));
        return Math.min(delay, 30000L);
    }
}
