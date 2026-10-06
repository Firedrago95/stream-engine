package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FakeAnalysisRepository implements AnalysisRepository {

    private final Map<String, List<AnalysisDataPoint>> rawHistoryStorage = new ConcurrentHashMap<>();
    private final Map<String, List<AnalysisDataPoint>> summaryHistoryStorage = new ConcurrentHashMap<>();

    public void setRawHistory(String streamId, String sessionId, List<AnalysisDataPoint> points) {
        rawHistoryStorage.put(key(streamId, sessionId), new ArrayList<>(points));
    }

    public void setSummaryHistory(String streamId, String sessionId, List<AnalysisDataPoint> points) {
        summaryHistoryStorage.put(key(streamId, sessionId), new ArrayList<>(points));
    }

    @Override
    public void save(AnalysisSignal signal) {}

    @Override
    public void saveAll(List<AnalysisSignal> signals) {}

    @Override
    public Set<String> findChannelsWithRecentSignals(Collection<String> streamIds, Instant threshold) {
        return Collections.emptySet();
    }

    @Override
    public List<AnalysisDataPoint> findRawHistory(String streamId, String sessionId) {
        return rawHistoryStorage.getOrDefault(key(streamId, sessionId), List.of());
    }

    @Override
    public List<AnalysisDataPoint> findSummaryHistory(String streamId, String sessionId) {
        return summaryHistoryStorage.getOrDefault(key(streamId, sessionId), List.of());
    }

    private String key(String streamId, String sessionId) {
        return streamId + ":" + sessionId;
    }
}
