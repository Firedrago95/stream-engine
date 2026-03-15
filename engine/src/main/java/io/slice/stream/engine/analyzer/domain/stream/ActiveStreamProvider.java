package io.slice.stream.engine.analyzer.domain.stream;

import io.slice.stream.engine.core.model.StreamTarget;
import java.util.List;

public interface ActiveStreamProvider {

    List<String> getActiveStreamIds();

    List<StreamTarget> getActiveStreamTargets();
}
