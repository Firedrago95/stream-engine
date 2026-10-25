package io.slice.stream.collector.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatCollectorFactory;
import io.slice.stream.core.model.StreamTarget;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatManager {

    private final Map<String, ActiveCollector> chatCollectors = new ConcurrentHashMap<>();
    private final ChatCollectorFactory chatCollectorFactory;
    private final Executor executor;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Phaser pendingConnectTasks = new Phaser(1);

    public ChatManager(
        ChatCollectorFactory chatCollectorFactory,
        Executor executor,
        MeterRegistry meterRegistry
    ) {
        this.chatCollectorFactory = chatCollectorFactory;
        this.executor = executor;
        registerMetrics(meterRegistry);
    }

    private void registerMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("collector.websocket.connections.active", chatCollectors, Map::size)
            .description("현재 활성 상태인 치지직 WebSocket 수집기 수")
            .register(meterRegistry);

        Gauge.builder("engine.websocket.connections.active", chatCollectors, Map::size)
            .description("기존 엔진 대시보드 호환용 치지직 WebSocket 수집기 수")
            .register(meterRegistry);
    }

    public void manageStreams(Set<StreamTarget> newStreamTargets, Set<StreamTarget> closedStreams) {
        closeStreams(closedStreams);
        openStreams(newStreamTargets);
    }

    private void closeStreams(Set<StreamTarget> closedStreams) {
        if (closedStreams == null || closedStreams.isEmpty()) {
            return;
        }
        closedStreams.forEach(closedStream -> {
            ActiveCollector activeCollector = chatCollectors.remove(closedStream.channelId());
            if (activeCollector != null) {
                activeCollector.collector().disconnect();
                log.info("[수집기 종료] 채널 ID: {}", closedStream.channelId());
            }
        });
    }

    private void openStreams(Set<StreamTarget> newStreamTargets) {
        if (shuttingDown.get() || newStreamTargets == null || newStreamTargets.isEmpty()) {
            return;
        }

        List<StreamTarget> targets = newStreamTargets.stream()
            .filter(this::isValidTarget)
            .toList();

        for (int i = 0; i < targets.size(); i++) {
            if (shuttingDown.get()) {
                return;
            }
            int index = i;
            StreamTarget streamTarget = targets.get(i);
            pendingConnectTasks.register();

            try {
                executor.execute(() -> {
                    try {
                        connectWithDelay(streamTarget, index);
                    } finally {
                        pendingConnectTasks.arriveAndDeregister();
                    }
                });
            } catch (RuntimeException e) {
                pendingConnectTasks.arriveAndDeregister();
                throw e;
            }
        }
    }

    private void connectWithDelay(StreamTarget streamTarget, int index) {
        try {
            if (shuttingDown.get()) {
                return;
            }
            if (index > 0) {
                Thread.sleep(index * 600L);
            }
            if (shuttingDown.get()) {
                return;
            }
            chatCollectors.compute(streamTarget.channelId(), (id, existing) -> {
                if (shuttingDown.get()) {
                    if (existing != null) {
                        existing.collector().disconnect();
                    }
                    return null;
                }
                if (existing != null) {
                    if (existing.chatChannelId().equals(streamTarget.chatChannelId())) {
                        return existing;
                    }
                    existing.collector().disconnect();
                    log.info("[채팅방 변경 감지] 이전 수집기를 종료하고 새 채팅방으로 교체합니다. 채널 ID: {}, 이전 채팅방: {}, 신규 채팅방: {}",
                        id, existing.chatChannelId(), streamTarget.chatChannelId());
                }
                log.info("[수집기 시작] 채널 ID: {}, 스트리머: {}, 채팅방: {}",
                    streamTarget.channelId(), streamTarget.channelName(), streamTarget.chatChannelId());
                ChatCollector newCollector = chatCollectorFactory.start(streamTarget);
                if (shuttingDown.get()) {
                    newCollector.disconnect();
                    return null;
                }
                return new ActiveCollector(streamTarget.chatChannelId(), newCollector);
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[수집기 시작 실패] 채널 ID: {}, 사유: {}", streamTarget.channelId(), e.getMessage(), e);
        }
    }

    private boolean isValidTarget(StreamTarget target) {
        return target != null && target.chatChannelId() != null && !target.chatChannelId().isBlank();
    }

    public Set<String> getActiveChannelIds() {
        return Collections.unmodifiableSet(chatCollectors.keySet());
    }

    public boolean isCollecting(String channelId, String chatChannelId) {
        ActiveCollector active = chatCollectors.get(channelId);
        return active != null && active.chatChannelId().equals(chatChannelId);
    }

    public boolean isCollecting(String channelId) {
        return chatCollectors.containsKey(channelId);
    }

    @PreDestroy
    public void shutdownAll() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        int phase = pendingConnectTasks.arrive();
        try {
            pendingConnectTasks.awaitAdvanceInterruptibly(phase, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            log.warn("[수집기 종료 대기 시간 초과] 진행 중인 연결 작업을 기다리던 중 타임아웃이 발생했습니다.");
        }

        chatCollectors.forEach((channelId, activeCollector) -> {
            try {
                activeCollector.collector().disconnect();
                log.info("[수집기 종료 정산] 컨테이너 종료로 파일 마감 처리: 채널 ID={}", channelId);
            } catch (Exception e) {
                log.error("[수집기 종료 오류] 채널 ID={}", channelId, e);
            }
        });
        chatCollectors.clear();
    }

    private record ActiveCollector(String chatChannelId, ChatCollector collector) {}
}
