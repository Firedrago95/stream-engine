package io.slice.stream.engine.analyzer.domain;

import java.util.List;

public interface HighlightSignalClient {

    void send(List<AnalysisSignal> signals);
}
