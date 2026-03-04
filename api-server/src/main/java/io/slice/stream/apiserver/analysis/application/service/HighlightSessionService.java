package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionService {

    private final JpaHighlightEventRepository repository;

    @Value("${highlight.leading-buffer}")
    private Duration leadingBuffer;

    @Value("${highlight.cooldown}")
    private Duration cooldown;

    @Retryable(
        maxRetries = 2,
        delay = 1000
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSignal(AnalysisSignal signal) {
        Optional<HighlightEventEntity> ongoingSession = repository.findOngoingSession(signal.streamId(), "ONGOING");

        if ("PEAK".equals(signal.status())) {
            ongoingSession.ifPresentOrElse(
                session -> extendSession(signal, session),
                () -> startNewSession(signal)
            );
        } else {
            ongoingSession.ifPresent(session -> checkAndFinishSession(signal, session));
        }
    }

    private void extendSession(AnalysisSignal signal, HighlightEventEntity session) {
        session.updatePeakFirepower(signal.firepower());
        session.updateLastPeakTime(signal.timestamp());
        log.info("[Session-Extend] 하이라이트 세션 확장 Stream: {}, Peak: {}", signal.streamId(), signal.firepower());
    }

    private void startNewSession(AnalysisSignal signal) {
        Instant adjustedStart = signal.timestamp().minus(leadingBuffer);
        HighlightEventEntity newSession = new HighlightEventEntity(
            signal.streamId(),
            adjustedStart,
            signal.timestamp(),
            signal.firepower()
        );
        repository.save(newSession);
        log.info("[Session-Start] 하이라이트 세션 시작 Stream: {}, StartAt: {}", signal.streamId(), adjustedStart);
    }

    private void checkAndFinishSession(AnalysisSignal signal, HighlightEventEntity session) {
        if (signal.timestamp().isAfter(session.getLastPeakTime().plus(cooldown))) {
            Instant finalEndTime = session.getLastPeakTime().plus(cooldown);
            session.finish(finalEndTime);
            log.info("[Session-Finish] 하이라이트 세션 종료 Stream:{}, Duration: {}",
                signal.streamId(), Duration.between(session.getStartTime(), finalEndTime));
        }
    }
}
