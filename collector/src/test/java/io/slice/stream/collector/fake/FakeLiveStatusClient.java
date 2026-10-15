package io.slice.stream.collector.fake;

import io.slice.stream.collector.domain.LiveStatusClient;
import io.slice.stream.collector.domain.LiveStatusResult;
import io.slice.stream.core.model.StreamTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeLiveStatusClient implements LiveStatusClient {

    private final Map<String, StreamTarget> openStreams = new ConcurrentHashMap<>();
    private final Set<String> failedChannelIds = ConcurrentHashMap.newKeySet();

    public void setOpenStream(StreamTarget streamTarget) {
        openStreams.put(streamTarget.channelId(), streamTarget);
        failedChannelIds.remove(streamTarget.channelId());
    }

    public void removeOpenStream(String channelId) {
        openStreams.remove(channelId);
        failedChannelIds.remove(channelId);
    }

    public void setFailedChannel(String channelId) {
        failedChannelIds.add(channelId);
    }

    public void clear() {
        openStreams.clear();
        failedChannelIds.clear();
    }

    @Override
    public LiveStatusResult fetchLiveStatuses(Set<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return LiveStatusResult.empty();
        }

        List<StreamTarget> openList = new ArrayList<>();
        Set<String> closedSet = new HashSet<>();
        Set<String> failedSet = new HashSet<>();

        for (String channelId : channelIds) {
            if (failedChannelIds.contains(channelId)) {
                failedSet.add(channelId);
            } else if (openStreams.containsKey(channelId)) {
                openList.add(openStreams.get(channelId));
            } else {
                closedSet.add(channelId);
            }
        }

        return new LiveStatusResult(openList, closedSet, failedSet);
    }
}
