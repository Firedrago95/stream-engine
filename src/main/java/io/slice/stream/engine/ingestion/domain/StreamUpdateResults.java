package io.slice.stream.engine.ingestion.domain;

import java.util.Set;

public record StreamUpdateResults(
    Set<String> newStreamIds,
    Set<String> closedStreamIds
) {

}
