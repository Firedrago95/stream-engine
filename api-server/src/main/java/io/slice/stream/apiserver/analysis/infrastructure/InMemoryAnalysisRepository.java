package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAnalysisRepository implements AnalysisRepository {

    private final Map<String, List<AnalysisSignal>> store = new ConcurrentHashMap<>();

    @Override
    public void save(AnalysisSignal signal) {
        store.computeIfAbsent(signal.streamId(), k -> new CopyOnWriteArrayList<>())
            .add(signal);

        List<AnalysisSignal> signals = store.get(signal.streamId());
        if (signals.size() > 100) {
            signals.removeFirst();
        }
    }

    @Override
    public List<AnalysisSignal> findRecentSignals(String streamId, int limit) {
        List<AnalysisSignal> signals = store.getOrDefault(streamId, Collections.emptyList());

        int size = signals.size();
        if (size == 0) return Collections.emptyList();

        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(signals.subList(fromIndex, size));
    }
}
