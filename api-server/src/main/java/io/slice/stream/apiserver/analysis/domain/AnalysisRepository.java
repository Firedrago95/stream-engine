package io.slice.stream.apiserver.analysis.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AnalysisRepository {

    void save(AnalysisSignal signal);

    List<AnalysisSignal> findRecentSignals(String streamId, int limit);

    Set<String> findChannelsWithRecentSignals(Collection<String> streamIds);
}
