package io.slice.stream.collector.domain;

import io.slice.stream.core.model.StreamTarget;
import java.util.List;
import java.util.Set;

public interface LiveStatusClient {

    List<StreamTarget> fetchOpenStreams(Set<String> channelIds);
}
