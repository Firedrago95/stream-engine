package io.slice.stream.apiserver.analysis.domain.event;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;

public record SignalSavedEvent(AnalysisSignal signal) {

}
