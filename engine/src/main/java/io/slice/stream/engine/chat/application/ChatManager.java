package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatManager {

    private final Map<String, ChatCollector> chatCollectors = new ConcurrentHashMap<>();
    private final ChatCollectorFactory chatCollectorFactory;

    public void manageStreams(Set<StreamTarget> newStreamTargets, Set<String> closedChatChannelIds) {
        if (!newStreamTargets.isEmpty()) {
            List<StreamTarget> targets = newStreamTargets.stream().toList();

            for (int i = 0; i < targets.size(); i++) {
                int index = i;
                StreamTarget streamTarget = targets.get(i);

                Thread.startVirtualThread(() -> {
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

        if (!closedChatChannelIds.isEmpty()) {
            closedChatChannelIds.forEach(channelId -> {
                ChatCollector collector = chatCollectors.remove(channelId);
                if (collector != null) {
                    collector.disconnect();
                }
            });
        }
    }

    private ChatCollector manageNewStreams(StreamTarget streamTarget) {
        return chatCollectorFactory.start(streamTarget);
    }
}
