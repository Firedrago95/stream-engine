package io.slice.stream.engine.chat.application;

import io.slice.stream.engine.chat.domain.ChatCollector;
import io.slice.stream.engine.chat.domain.ChatCollectorFactory;
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

    public void manageStreams(Set<String> newStreamIds, Set<String> closedStreamIds) {
        if (!newStreamIds.isEmpty()) {
            newStreamIds.forEach(stream -> {
                chatCollectors.put(stream, manageNewStreams(stream));
            });
        }

        if (!closedStreamIds.isEmpty()) {
            closedStreamIds.forEach(streamId -> {
                ChatCollector collector = chatCollectors.remove(streamId);
                if (collector != null) {
                    collector.disconnect();
                }
            });
        }
    }

    private ChatCollector manageNewStreams(String streamId) {
        return chatCollectorFactory.start(streamId);
    }
}
