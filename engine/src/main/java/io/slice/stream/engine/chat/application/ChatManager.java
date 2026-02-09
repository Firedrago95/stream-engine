package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
import io.slice.stream.engine.core.model.StreamTarget;
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
            newStreamTargets.forEach(streamTarget -> {
                chatCollectors.put(streamTarget.chatChannelId(), manageNewStreams(streamTarget));
            });
        }

        if (!closedChatChannelIds.isEmpty()) {
            closedChatChannelIds.forEach(chatChannelId -> {
                ChatCollector collector = chatCollectors.remove(chatChannelId);
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
