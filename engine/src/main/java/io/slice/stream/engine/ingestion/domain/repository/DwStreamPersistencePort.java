package io.slice.stream.engine.ingestion.domain.repository;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.time.Instant;
import java.util.List;

public interface DwStreamPersistencePort {

    void persistSnapshot(List<StreamTarget> liveTargets, StreamUpdateResults updateResults, Instant recordedAt);
}
