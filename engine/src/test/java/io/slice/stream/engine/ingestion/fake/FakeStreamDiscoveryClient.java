package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeStreamDiscoveryClient implements StreamDiscoveryClient {

    private List<StreamTarget> topLiveStreams = new ArrayList<>();
    private final Map<String, StreamTarget> detailedStreams = new ConcurrentHashMap<>();
    private int fetchLiveStreamsCallCount = 0;
    private final Set<String> requestedDetailChannelIds = new HashSet<>();
    private boolean throwOnFetch = false;

    public void setTopLiveStreams(List<StreamTarget> topLiveStreams) {
        this.topLiveStreams = new ArrayList<>(topLiveStreams);
    }

    public void addDetailedStream(StreamTarget streamTarget) {
        detailedStreams.put(streamTarget.channelId(), streamTarget);
    }

    public void setThrowOnFetch(boolean throwOnFetch) {
        this.throwOnFetch = throwOnFetch;
    }

    public int getFetchLiveStreamsCallCount() {
        return fetchLiveStreamsCallCount;
    }

    public Set<String> getRequestedDetailChannelIds() {
        return Collections.unmodifiableSet(requestedDetailChannelIds);
    }

    @Override
    public List<StreamTarget> fetchTopLiveStreams(int limit) {
        if (throwOnFetch) {
            throw new RuntimeException("치지직 API 오류 모의 예외");
        }
        return Collections.unmodifiableList(topLiveStreams);
    }

    @Override
    public List<StreamTarget> fetchLiveStreams(Set<String> channelIds) {
        fetchLiveStreamsCallCount++;
        if (channelIds == null || channelIds.isEmpty()) {
            return Collections.emptyList();
        }
        requestedDetailChannelIds.addAll(channelIds);
        return channelIds.stream()
            .map(detailedStreams::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<StreamTarget> fetchLiveStreamsForChat(Set<StreamTarget> targets) {
        return Collections.emptyList();
    }
}
