package io.slice.stream.apiserver.analysis.domain;

import java.util.List;

public interface AnalysisRepository {

    void save(AnalysisSignal signal);

    List<AnalysisSignal> findRecentSignals(String streamId, int limit);
}
