package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCommandService {

    private final ApplicationEventPublisher eventPublisher;

    public void processSignals(List<AnalysisSignal> signals) {
        signals.forEach(signal -> {
            log.info("[Analysis] 신호 수신 - 스트림: {}, 상태: {}",signal.streamId(), signal.status());
            eventPublisher.publishEvent(signal);
        });
    }
}
