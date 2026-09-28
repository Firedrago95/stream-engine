package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.application.StreamTierManager;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.util.HashMap;
import java.util.Map;

public class FakeStreamTierManager extends StreamTierManager {

    private StreamTierInfo defaultTierInfo;
    private final Map<String, StreamTierInfo> streamTierMap = new HashMap<>();

    public FakeStreamTierManager() {
        super(null, null, null);
    }

    public void setDefaultTierInfo(StreamTierInfo defaultTierInfo) {
        this.defaultTierInfo = defaultTierInfo;
    }

    public void setTierInfo(String streamId, StreamTierInfo tierInfo) {
        streamTierMap.put(streamId, tierInfo);
    }

    @Override
    public StreamTierInfo getTierInfo(String streamId, int currentViewers) {
        if (streamTierMap.containsKey(streamId)) {
            return streamTierMap.get(streamId);
        }
        return defaultTierInfo;
    }

    @Override
    public void refreshAllTiers() {}
}
