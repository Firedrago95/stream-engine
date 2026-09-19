package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.LiveStatusClient;
import io.slice.stream.core.model.StreamTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeLiveStatusClient implements LiveStatusClient {

    private final Map<String, StreamTarget> openStreams = new ConcurrentHashMap<>();

    public void setOpenStream(StreamTarget streamTarget) {
        openStreams.put(streamTarget.channelId(), streamTarget);
    }

    public void removeOpenStream(String channelId) {
        openStreams.remove(channelId);
    }

    public void clear() {
        openStreams.clear();
    }

    @Override
    public List<StreamTarget> fetchOpenStreams(Set<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        return channelIds.stream()
            .map(openStreams::get)
            .filter(Objects::nonNull)
            .toList();
    }
}
