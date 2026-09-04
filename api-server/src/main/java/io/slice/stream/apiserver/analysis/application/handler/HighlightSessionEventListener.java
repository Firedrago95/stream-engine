package io.slice.stream.apiserver.analysis.application.handler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.slice.stream.apiserver.analysis.application.service.HighlightSessionService;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class HighlightSessionEventListener {

    private final HighlightSessionService highlightSessionService;
    private final Timer processTimer;
    private final Counter failureCounter;

    public HighlightSessionEventListener(HighlightSessionService highlightSessionService, MeterRegistry meterRegistry) {
        this.highlightSessionService = highlightSessionService;
        this.processTimer = Timer.builder("apiserver.highlight.process.duration")
            .description("하이라이트 세션 비동기 처리 소요 시간")
            .publishPercentiles(0.95, 0.99)
            .register(meterRegistry);
        this.failureCounter = Counter.builder("apiserver.highlight.process.failures")
            .description("하이라이트 세션 비동기 처리 실패 누적 수")
            .register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalSaved(SignalSavedEvent event) {
        try {
            processTimer.record(() -> highlightSessionService.handleSignal(event.signal()));
        } catch (Exception e) {
            failureCounter.increment();
            log.error("[Highlight] 세션 처리 실패 - 스트림: {}", event.signal(), e);
        }
    }
}
