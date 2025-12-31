package io.slice.stream.engine.ingestion.domain;

import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;

public interface StreamDiscoveryClient {

    List<StreamTarget> fetchTopLiveStreams(int limit);
}
