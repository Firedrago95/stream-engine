package io.slice.stream.engine.analyzer.fake;

import io.slice.stream.engine.analyzer.domain.signal.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.signal.HighlightSignalClient;
import java.util.ArrayList;
import java.util.List;

public class FakeHighlightSignalClient implements HighlightSignalClient {

    private final List<List<AnalysisSignal>> sentBatches = new ArrayList<>();

    @Override
    public void send(List<AnalysisSignal> signals) {
        if (signals != null) {
            sentBatches.add(List.copyOf(signals));
        }
    }

    public List<List<AnalysisSignal>> getSentBatches() {
        return List.copyOf(sentBatches);
    }

    public List<AnalysisSignal> getLastSentSignals() {
        if (sentBatches.isEmpty()) {
            return List.of();
        }
        return sentBatches.get(sentBatches.size() - 1);
    }

    public int getSentCount() {
        return sentBatches.size();
    }

    public void clear() {
        sentBatches.clear();
    }
}
