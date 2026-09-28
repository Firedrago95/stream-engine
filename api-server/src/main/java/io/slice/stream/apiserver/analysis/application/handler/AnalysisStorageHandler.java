package io.slice.stream.apiserver.analysis.application.handler;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import io.slice.stream.apiserver.analysis.domain.event.SignalsReceivedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStorageHandler {

    private final AnalysisRepository analysisRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void handleSignalsReceived(SignalsReceivedEvent event) {
        List<AnalysisSignal> signals = event.signals();
        if (signals.isEmpty()) {
            return;
        }

        log.debug("[Storage] 분석 신호 일괄 저장 - 건수 : {}", signals.size());
        analysisRepository.saveAll(signals);

        for (AnalysisSignal signal : signals) {
            eventPublisher.publishEvent(new SignalSavedEvent(signal));
        }
    }

    @EventListener
    @Transactional
    public void handleAnalysisSignal(AnalysisSignal signal) {
        log.debug("[Storage] 분석 신호 저장 - 스트림 : {}, 상태 : {}", signal.streamId(), signal.status());
        analysisRepository.save(signal);

        eventPublisher.publishEvent(new SignalSavedEvent(signal));
    }
}
