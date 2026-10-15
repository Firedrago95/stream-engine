package io.slice.stream.collector.domain;

import java.util.Set;

public interface LiveStatusClient {

    LiveStatusResult fetchLiveStatuses(Set<String> channelIds);
}
