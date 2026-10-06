package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.domain.tier.SessionTierRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FakeSessionTierRepository implements SessionTierRepository {

    private final Map<Long, Long> storage = new ConcurrentHashMap<>();

    @Override
    public void save(long liveId, long noiseFloor) {
        if (liveId > 0) {
            storage.put(liveId, noiseFloor);
        }
    }

    @Override
    public Optional<Long> findByLiveId(long liveId) {
        return Optional.ofNullable(storage.get(liveId));
    }

    public void clear() {
        storage.clear();
    }
}
