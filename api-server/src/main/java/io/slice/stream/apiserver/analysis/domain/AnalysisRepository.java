package io.slice.stream.apiserver.analysis.domain;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AnalysisRepository {

    void save(AnalysisSignal signal);

    List<AnalysisSignal> findRecentSignals(String streamId, int limit);

    Set<String> findChannelsWithRecentSignals(Collection<String> streamIds);

    List<LocalDate> findAvailableDates(String streamId, LocalDate beforeDate, int limit);

    List<AnalysisDataPoint> findRawHistory(String streamId, Instant start, Instant end);

    List<AnalysisDataPoint> findSummaryHistory(String streamId, Instant start, Instant end);
}
