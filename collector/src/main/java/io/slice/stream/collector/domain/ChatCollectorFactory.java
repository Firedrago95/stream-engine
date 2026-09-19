package io.slice.stream.collector.domain;

import io.slice.stream.core.model.StreamTarget;

public interface ChatCollectorFactory {

    ChatCollector start(StreamTarget streamTarget);
}
