package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.DwStreamPersistencePort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FakeDwStreamPersistencePort implements DwStreamPersistencePort {

    private int persistCallCount = 0;
    private List<StreamTarget> lastPersistedTargets = new ArrayList<>();
    private StreamUpdateResults lastUpdateResults;
    private Instant lastRecordedAt;
    private boolean throwOnPersist = false;

    @Override
    public void persistSnapshot(List<StreamTarget> liveTargets, StreamUpdateResults updateResults, Instant recordedAt) {
        if (throwOnPersist) {
            throw new RuntimeException("DW 저장소 일시 오류");
        }
        this.persistCallCount++;
        this.lastPersistedTargets = new ArrayList<>(liveTargets);
        this.lastUpdateResults = updateResults;
        this.lastRecordedAt = recordedAt;
    }

    public int getPersistCallCount() {
        return persistCallCount;
    }

    public List<StreamTarget> getLastPersistedTargets() {
        return lastPersistedTargets;
    }

    public StreamUpdateResults getLastUpdateResults() {
        return lastUpdateResults;
    }

    public Instant getLastRecordedAt() {
        return lastRecordedAt;
    }

    public void setThrowOnPersist(boolean throwOnPersist) {
        this.throwOnPersist = throwOnPersist;
    }
}
