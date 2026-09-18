package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.BackoffPolicy;
import io.slice.stream.engine.chat.domain.ChatClient;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatMessageListener;
import io.slice.stream.engine.chat.domain.model.ChatMessage;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatConnectionManager implements ChatCollector, ChatMessageListener {

    private static final BackoffPolicy DEFAULT_BACKOFF_POLICY = BackoffPolicy.exponential(1000L, 30000L);

    private final ChatClient chatClient;
    private final ChatMessageListener downstreamListener;
    private final String chatChannelId;
    private final String channelId;
    private final ExecutorService executorService;
    private final BackoffPolicy backoffPolicy;

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile boolean isManualDisconnect = false;
    private volatile int retryCount = 0;

    public ChatConnectionManager(ChatClient chatClient, ChatMessageListener downstreamListener, String chatChannelId, String channelId, ExecutorService executorService) {
        this(chatClient, downstreamListener, chatChannelId, channelId, executorService, DEFAULT_BACKOFF_POLICY);
    }

    public ChatConnectionManager(ChatClient chatClient, ChatMessageListener downstreamListener, String chatChannelId, String channelId, ExecutorService executorService, BackoffPolicy backoffPolicy) {
        this.chatClient = chatClient;
        this.downstreamListener = downstreamListener;
        this.chatChannelId = chatChannelId;
        this.channelId = channelId;
        this.executorService = executorService;
        this.backoffPolicy = backoffPolicy;
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
        log.info("[{}] 수동으로 연결을 종료했습니다.", chatChannelId);
    }

    private void connect() {
        if (isManualDisconnect) {
            log.info("[{}] 수동으로 연결이 종료되어, 재연결을 시도하지 않습니다.", chatChannelId);
            return;
        }

        if (chatChannelId == null || chatChannelId.isBlank()) {
            log.warn("[{}] 유효하지 않은 chatChannelId 입니다. 연결을 건너뜁니다.", channelId);
            return;
        }

        try {
            log.info("[{}] 채팅 채널 연결을 시도합니다.", chatChannelId);
            chatClient.connect(channelId, chatChannelId, this);
        } catch (Exception e) {
            log.error("[{}] 채팅 연결에 실패했습니다.", chatChannelId, e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isManualDisconnect) return;

        if (isReconnecting.compareAndSet(false, true)) {
            long delayMillis = calculateBackoffDelay();
            retryCount++;

            executorService.submit(() -> {
                try {
                    log.info("[{}] {}ms 후 재연결을 시도합니다. (시도 횟수: {})", chatChannelId, delayMillis, retryCount);
                    Thread.sleep(delayMillis);
                    connect();
                } catch (InterruptedException e) {
                    log.warn("[{}] 재연결 대기 중 스레드가 중단되었습니다.", chatChannelId);
                    Thread.currentThread().interrupt();
                } finally {
                    isReconnecting.set(false);
                }
            });
        }
    }

    private long calculateBackoffDelay() {
        return backoffPolicy.calculateDelay(retryCount);
    }

    @Override
    public void onMessages(List<ChatMessage> messages) {
        downstreamListener.onMessages(messages);
    }

    @Override
    public void onConnected() {
        log.info("[{}] 채팅 채널에 성공적으로 연결되었습니다.", chatChannelId);
        retryCount = 0;
    }

    @Override
    public void onDisconnected() {
        log.warn("[{}] 채팅 채널 연결이 끊어졌습니다. 재연결을 시도합니다.", chatChannelId);
        scheduleReconnect();
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("[{}] 채팅 채널에서 오류가 발생했습니다. 재연결을 시도합니다.", chatChannelId, throwable);
        scheduleReconnect();
    }
}
