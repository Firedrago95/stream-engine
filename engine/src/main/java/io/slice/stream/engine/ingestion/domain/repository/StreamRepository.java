package io.slice.stream.engine.ingestion.domain.repository;

import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;
import java.util.Set;

public interface StreamRepository {

    Set<String> getActiveChannelIds();

    List<StreamTarget> getStreamTargets(List<String> channelIds);

    void sync(Set<StreamTarget> closedStreams, List<StreamTarget> activeTargets);
}
