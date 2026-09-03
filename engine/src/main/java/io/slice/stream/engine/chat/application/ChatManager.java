package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatManager {

    private final Map<String, ChatCollector> chatCollectors = new ConcurrentHashMap<>();
    private final ChatCollectorFactory chatCollectorFactory;
    private final ExecutorService virtualThreadExecutor;
    private final ActiveStreamProvider activeStreamProvider;

    public void manageStreams(Set<StreamTarget> newStreamTargets, Set<StreamTarget> closedStreams) {
        if (!closedStreams.isEmpty()) {
            closedStreams.forEach(closedStream -> {
                ChatCollector collector = chatCollectors.remove(closedStream.channelId());
                if (collector != null) {
                    collector.disconnect();
                }
            });
        }

        if (!newStreamTargets.isEmpty()) {
            List<StreamTarget> targets = newStreamTargets.stream()
                .filter(this::isValidTarget)
                .toList();

            for (int i = 0; i < targets.size(); i++) {
                int index = i;
                StreamTarget streamTarget = targets.get(i);

                virtualThreadExecutor.submit(() -> {
                    try {
                        Thread.sleep(index * 600L);
                        chatCollectors.computeIfAbsent(streamTarget.channelId(),
                            id -> manageNewStreams(streamTarget));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }

    private boolean isValidTarget(StreamTarget target) {
        return target.chatChannelId() != null && !target.chatChannelId().isBlank();
    }

    private ChatCollector manageNewStreams(StreamTarget streamTarget) {
        return chatCollectorFactory.start(streamTarget);
    }

    @Scheduled(fixedRate = 60000)
    public void reconcile() {
        List<StreamTarget> activeTargets = activeStreamProvider.getActiveStreamTargets();
        if (activeTargets == null) {
            return;
        }

        Set<String> activeChannelIds = activeTargets.stream()
            .map(StreamTarget::channelId)
            .collect(Collectors.toSet());

        Set<StreamTarget> missingTargets = activeTargets.stream()
            .filter(target -> !chatCollectors.containsKey(target.channelId()))
            .collect(Collectors.toSet());

        Set<StreamTarget> closedTargets = chatCollectors.keySet().stream()
            .filter(channelId -> !activeChannelIds.contains(channelId))
            .map(channelId -> new StreamTarget(channelId, null, null, 0L, null, 0, null, null, null))
            .collect(Collectors.toSet());

        if (!missingTargets.isEmpty() || !closedTargets.isEmpty()) {
            manageStreams(missingTargets, closedTargets);
        }
    }
}
