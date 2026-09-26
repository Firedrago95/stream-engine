package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeTargetStreamPool extends TargetStreamPool {

    private final Set<String> targetChannels = ConcurrentHashMap.newKeySet();

    public FakeTargetStreamPool() {
        super(null, null);
    }

    public void setTargetChannels(Set<String> channels) {
        targetChannels.clear();
        if (channels != null) {
            targetChannels.addAll(channels);
        }
    }

    public void addTargetChannel(String channelId) {
        if (channelId != null && !channelId.isBlank()) {
            targetChannels.add(channelId);
        }
    }

    @Override
    public Set<String> getAllActiveTargetChannels() {
        return Collections.unmodifiableSet(targetChannels);
    }

    @Override
    public boolean isTarget(String channelId) {
        return targetChannels.contains(channelId);
    }
}
