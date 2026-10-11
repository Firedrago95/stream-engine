package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeStreamerLeaderboardQueryService extends StreamerLeaderboardQueryService {

    private final Map<String, Integer> cachedAverageViewersMap = new ConcurrentHashMap<>();

    public FakeStreamerLeaderboardQueryService() {
        super(null, null, null, null, null);
    }

    public void setCachedAverageViewers(String channelId, int averageViewers) {
        cachedAverageViewersMap.put(channelId, averageViewers);
    }

    @Override
    public Optional<Integer> getCachedAverageViewers(String channelId) {
        return Optional.ofNullable(cachedAverageViewersMap.get(channelId));
    }
}
