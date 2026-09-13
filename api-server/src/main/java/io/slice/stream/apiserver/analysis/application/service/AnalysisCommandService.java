package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.stream.application.StreamSessionService;
import java.time.Instant;
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
    private final StreamSessionService streamSessionService;

    public void processSignals(List<AnalysisSignal> signals) {
        log.info("[Analysis] 신호 수신 - {}건의 방송 화력 신호 수신 완료", signals.size());

        signals.forEach(rawSignal -> {
            Instant sessionStartedAt = rawSignal.offsetMs() != null
                ? rawSignal.timestamp().minusMillis(rawSignal.offsetMs())
                : rawSignal.timestamp();

            String currentSessionId = streamSessionService.getOrCreateActiveSession(
                rawSignal.streamId(), rawSignal.sessionId(), sessionStartedAt
            );

            AnalysisSignal signalWithSession = AnalysisSignal.of(
                rawSignal.streamId(),
                currentSessionId,
                rawSignal.status(),
                rawSignal.timestamp(),
                rawSignal.firepower(),
                rawSignal.offsetMs()
            );

            eventPublisher.publishEvent(signalWithSession);
        });
    }
}
