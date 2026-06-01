package io.slice.stream.engine.ingestion.domain.client;

import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;
import java.util.Set;

public interface StreamDiscoveryClient {

    List<StreamTarget> fetchTopLiveStreams(int limit);

    List<StreamTarget> fetchLiveStreams(Set<String> channelIds);
}
