package io.slice.stream.apiserver.analysis.application.handler;

import io.slice.stream.apiserver.analysis.application.service.AnalysisSignalBufferService;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import io.slice.stream.apiserver.analysis.domain.event.SignalsReceivedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStorageHandler {

    private final AnalysisSignalBufferService bufferService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleSignalsReceived(SignalsReceivedEvent event) {
        List<AnalysisSignal> signals = event.signals();
        if (signals.isEmpty()) {
            return;
        }

        log.debug("[Storage] 분석 신호 버퍼 적재 - 건수 : {}", signals.size());
        bufferService.bufferSignals(signals);
        publishSignalSavedEvents(signals);
    }

    @EventListener
    public void handleAnalysisSignal(AnalysisSignal signal) {
        log.debug("[Storage] 분석 신호 버퍼 적재 - 스트림 : {}, 상태 : {}", signal.streamId(), signal.status());
        bufferService.bufferSignals(List.of(signal));
        eventPublisher.publishEvent(new SignalSavedEvent(signal));
    }

    private void publishSignalSavedEvents(List<AnalysisSignal> signals) {
        for (AnalysisSignal signal : signals) {
            eventPublisher.publishEvent(new SignalSavedEvent(signal));
        }
    }
}
