package io.slice.stream.engine.analyzer.domain;

import java.util.List;

public interface ActiveStreamProvider {

    List<String> getActiveStreamIds();
}
