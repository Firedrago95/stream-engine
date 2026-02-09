package io.slice.stream.engine.core.event;

import io.slice.stream.engine.core.model.StreamTarget;
import java.util.Set;

public record StreamChangedEvent(
    Set<StreamTarget> newStreamIds,
    Set<String> closedStreamIds
) {

}

