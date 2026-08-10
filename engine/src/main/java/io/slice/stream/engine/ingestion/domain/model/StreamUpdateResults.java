package io.slice.stream.engine.ingestion.domain.model;

import io.slice.stream.engine.core.model.StreamTarget;
import java.time.Instant;
import java.util.Set;

public record StreamUpdateResults(
    Set<StreamTarget> newStreams,
    Set<StreamTarget> closedStreamIds,
    Set<ChangedStream> changedStreams,
    Instant changedAt
) {

}

