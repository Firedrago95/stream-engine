package io.slice.stream.collector.domain;

import io.slice.stream.core.model.StreamTarget;
import java.util.List;

public interface TargetStreamReader {

    List<StreamTarget> getTargetStreams();
}
