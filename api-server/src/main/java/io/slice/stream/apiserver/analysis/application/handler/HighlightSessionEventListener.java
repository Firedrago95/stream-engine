package io.slice.stream.apiserver.analysis.application.handler;

import io.slice.stream.apiserver.analysis.application.service.HighlightSessionService;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionEventListener {

    private final HighlightSessionService highlightSessionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignalSaved(SignalSavedEvent event) {
        try {
            highlightSessionService.handleSignal(event.signal());
        } catch (Exception e) {
            log.error("[Highlight] 세션 처리 실패 - 스트림: {}", event.signal());
        }
    }
}
