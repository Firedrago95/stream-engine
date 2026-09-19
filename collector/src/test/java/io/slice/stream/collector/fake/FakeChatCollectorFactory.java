package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.ChatCollector;
import io.slice.stream.collector.domain.ChatCollectorFactory;
import io.slice.stream.core.model.StreamTarget;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeChatCollectorFactory implements ChatCollectorFactory {

    private final Map<String, FakeChatCollector> collectors = new ConcurrentHashMap<>();

    @Override
    public ChatCollector start(StreamTarget streamTarget) {
        FakeChatCollector collector = new FakeChatCollector(streamTarget.channelId());
        collector.start();
        collectors.put(streamTarget.channelId(), collector);
        return collector;
    }

    public FakeChatCollector getCollector(String channelId) {
        return collectors.get(channelId);
    }

    public Map<String, FakeChatCollector> getAllCollectors() {
        return collectors;
    }
}
