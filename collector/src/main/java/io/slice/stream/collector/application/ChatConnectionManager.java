package io.slice.stream.collector.application;

import io.slice.stream.collector.domain.BackoffPolicy;
import io.slice.stream.collector.domain.ChatClient;
import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatMessageListener;
import io.slice.stream.core.model.ChatMessage;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChatConnectionManager implements ChatCollector, ChatMessageListener {

    private static final BackoffPolicy DEFAULT_BACKOFF_POLICY = BackoffPolicy.exponential(1000L, 30000L);

    private final ChatClient chatClient;
    private final ChatMessageListener downstreamListener;
    private final String chatChannelId;
    private final String channelId;
    private final Executor executor;
    private final BackoffPolicy backoffPolicy;

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile boolean isManualDisconnect = false;
    private volatile int retryCount = 0;

    public ChatConnectionManager(
        ChatClient chatClient,
        ChatMessageListener downstreamListener,
        String chatChannelId,
        String channelId,
        Executor executor
    ) {
        this(chatClient, downstreamListener, chatChannelId, channelId, executor, DEFAULT_BACKOFF_POLICY);
    }

    public ChatConnectionManager(
        ChatClient chatClient,
        ChatMessageListener downstreamListener,
        String chatChannelId,
        String channelId,
        Executor executor,
        BackoffPolicy backoffPolicy
    ) {
        this.chatClient = chatClient;
        this.downstreamListener = downstreamListener;
        this.chatChannelId = chatChannelId;
        this.channelId = channelId;
        this.executor = executor;
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
        log.info("[채팅 연결 종료] 채널 ID: {}, 채팅방: {}", channelId, chatChannelId);
    }

    private void connect() {
        if (isManualDisconnect) {
            log.info("[수동 종료 상태] 재연결을 시도하지 않습니다. 채널 ID: {}", channelId);
            return;
        }

        if (chatChannelId == null || chatChannelId.isBlank()) {
            log.warn("[유효하지 않은 채팅방] 연결을 건너뜁니다. 채널 ID: {}", channelId);
            return;
        }

        try {
            log.info("[채팅 연결 시도] 채널 ID: {}, 채팅방: {}", channelId, chatChannelId);
            chatClient.connect(channelId, chatChannelId, this);
        } catch (Exception e) {
            log.error("[채팅 연결 실패] 채널 ID: {}, 채팅방: {}", channelId, chatChannelId, e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isManualDisconnect) {
            return;
        }

        if (isReconnecting.compareAndSet(false, true)) {
            long delayMillis = backoffPolicy.calculateDelay(retryCount);
            retryCount++;

            executor.execute(() -> {
                try {
                    log.info("[재연결 대기] 채널 ID: {}, {}ms 후 재연결 시도 (시도 횟수: {})", channelId, delayMillis, retryCount);
                    if (delayMillis > 0) {
                        Thread.sleep(delayMillis);
                    }
                } catch (InterruptedException e) {
                    log.warn("[재연결 중단] 대기 중 스레드가 인터럽트되었습니다. 채널 ID: {}", channelId);
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    isReconnecting.set(false);
                }
                connect();
            });
        }
    }

    boolean isReconnecting() {
        return isReconnecting.get();
    }

    @Override
    public void onMessages(List<ChatMessage> messages) {
        downstreamListener.onMessages(messages);
    }

    @Override
    public void onConnected() {
        log.info("[채팅 연결 성공] 채널 ID: {}, 채팅방: {}", channelId, chatChannelId);
        retryCount = 0;
        downstreamListener.onConnected();
    }

    @Override
    public void onDisconnected() {
        log.warn("[채팅 연결 끊김] 재연결을 시도합니다. 채널 ID: {}", channelId);
        downstreamListener.onDisconnected();
        scheduleReconnect();
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("[채팅 오류 발생] 재연결을 시도합니다. 채널 ID: {}", channelId, throwable);
        downstreamListener.onError(throwable);
        scheduleReconnect();
    }
}
