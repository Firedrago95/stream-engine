package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.TargetStreamReader;
import io.slice.stream.core.model.StreamTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeTargetStreamReader implements TargetStreamReader {

    private final Map<String, StreamTarget> targets = new ConcurrentHashMap<>();
    private boolean throwOnRead = false;

    public void addTarget(StreamTarget target) {
        targets.put(target.channelId(), target);
    }

    public void removeTarget(String channelId) {
        targets.remove(channelId);
    }

    public void clear() {
        targets.clear();
    }

    public void setThrowOnRead(boolean throwOnRead) {
        this.throwOnRead = throwOnRead;
    }

    @Override
    public List<StreamTarget> getTargetStreams() {
        if (throwOnRead) {
            throw new IllegalStateException("Redis connection failure simulation");
        }
        return new ArrayList<>(targets.values());
    }
}
