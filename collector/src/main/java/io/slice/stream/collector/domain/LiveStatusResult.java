package io.slice.stream.collector.domain;

import io.slice.stream.core.model.StreamTarget;
import java.util.List;
import java.util.Set;

public record LiveStatusResult(
    List<StreamTarget> openStreams,
    Set<String> closedChannelIds,
    Set<String> failedChannelIds
) {

    public static LiveStatusResult empty() {
        return new LiveStatusResult(List.of(), Set.of(), Set.of());
    }
}
