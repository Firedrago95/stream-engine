package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.TargetStreamReader;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeTargetStreamReader implements TargetStreamReader {

    private final Set<String> targetChannels = ConcurrentHashMap.newKeySet();

    public void addTarget(String channelId) {
        targetChannels.add(channelId);
    }

    public void removeTarget(String channelId) {
        targetChannels.remove(channelId);
    }

    public void setTargets(Set<String> channels) {
        targetChannels.clear();
        targetChannels.addAll(channels);
    }

    @Override
    public Set<String> getTargetChannels() {
        return Collections.unmodifiableSet(targetChannels);
    }
}
