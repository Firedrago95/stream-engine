package io.slice.stream.collector.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatCollectorFactory;
import io.slice.stream.core.model.StreamTarget;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatManager {

    private final Map<String, ActiveCollector> chatCollectors = new ConcurrentHashMap<>();
    private final ChatCollectorFactory chatCollectorFactory;
    private final Executor executor;

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
        if (newStreamTargets == null || newStreamTargets.isEmpty()) {
            return;
        }

        List<StreamTarget> targets = newStreamTargets.stream()
            .filter(this::isValidTarget)
            .toList();

        for (int i = 0; i < targets.size(); i++) {
            int index = i;
            StreamTarget streamTarget = targets.get(i);

            executor.execute(() -> {
                connectWithDelay(streamTarget, index);
            });
        }
    }

    private void connectWithDelay(StreamTarget streamTarget, int index) {
        try {
            if (index > 0) {
                Thread.sleep(index * 600L);
            }
            chatCollectors.compute(streamTarget.channelId(), (id, existing) -> {
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

    private record ActiveCollector(String chatChannelId, ChatCollector collector) {}
}
