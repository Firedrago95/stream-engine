package io.slice.stream.apiserver.analysis.domain.event;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import java.util.List;

public record SignalsReceivedEvent(List<AnalysisSignal> signals) {

    public SignalsReceivedEvent {
        signals = signals != null ? List.copyOf(signals) : List.of();
    }
}
