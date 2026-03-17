package io.slice.stream.apiserver.analysis.domain;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AnalysisRepository {

    void save(AnalysisSignal signal);

    List<AnalysisSignal> findRecentSignals(String streamId, int limit);

    Set<String> findChannelsWithRecentSignals(Collection<String> streamIds);

    List<AnalysisDataPoint> findRawHistory(String streamId, String sessionId);

    List<AnalysisDataPoint> findSummaryHistory(String streamId, String sessionId);
}
