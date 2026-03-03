package io.slice.stream.apiserver.analysis.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStorageHandler {

    private final AnalysisRepository analysisRepository;

    @EventListener
    @Transactional
    public void handleAnalysisSignal(AnalysisSignal signal) {
        log.debug("[Storage] 분석 신호 저장 - 스트림 : {}, 상태 : {}", signal.streamId(), signal.status());
        analysisRepository.save(signal);
    }
}
