package io.slice.stream.apiserver.analysis.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    public static final int FIND_LIMIT = 50;

    private final ApplicationEventPublisher eventPublisher;
    private final AnalysisRepository analysisRepository;

    public void processSignals(List<AnalysisSignal> signals) {
        signals.forEach(signal -> {
            log.info("[Analysis] 신호 수신 - 스트림: {}, 상태: {}",signal.streamId(), signal.status());
            eventPublisher.publishEvent(signal);
        });
    }

    public AnalysisResponse getRecentAnalysis(String streamId) {
        List<AnalysisSignal> signals = analysisRepository.findRecentSignals(streamId, FIND_LIMIT);

        List<AnalysisDataPoint> dataPoints = signals.stream()
            .map(s -> new AnalysisDataPoint(
                s.timestamp().toEpochMilli(),
                s.firepower(),
                s.status()
            ))
            .toList();

        return new AnalysisResponse(streamId, dataPoints);
    }
}
