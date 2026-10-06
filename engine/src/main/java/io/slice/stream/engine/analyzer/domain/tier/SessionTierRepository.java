package io.slice.stream.engine.analyzer.domain.tier;

import java.util.Optional;

public interface SessionTierRepository {

    void save(long liveId, long noiseFloor);

    Optional<Long> findByLiveId(long liveId);
}
