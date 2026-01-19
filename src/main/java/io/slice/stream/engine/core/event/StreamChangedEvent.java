package io.slice.stream.engine.core.event;

import java.util.Set;

public record StreamChangedEvent(
    Set<String> newStreamIds,
    Set<String> closedStreamIds
) {

}
