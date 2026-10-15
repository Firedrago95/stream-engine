package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HighlightSessionPersistenceService {

    private final JpaHighlightEventRepository repository;

    @Transactional
    public void updateDbSessionForPeak(AnalysisSignal signal, Duration leadingBuffer) {
        String streamId = signal.streamId();
        Optional<HighlightEventEntity> ongoingSession =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING");

        ongoingSession.ifPresentOrElse(
            session -> extendSession(signal, session),
            () -> startNewSession(signal, leadingBuffer)
        );
    }

    @Transactional
    public boolean finishSessionIfEligible(AnalysisSignal signal, Duration cooldown, Duration trailingBuffer) {
        String streamId = signal.streamId();
        Optional<HighlightEventEntity> ongoingSession =
            repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING");

        if (ongoingSession.isEmpty()) {
            return true;
        }

        HighlightEventEntity session = ongoingSession.get();
        Instant threshold = session.getLastPeakTime().plus(cooldown);

        if (!signal.timestamp().isBefore(threshold)) {
            finishSession(signal, session, trailingBuffer);
            return true;
        }
        return false;
    }

    @Transactional
    public List<String> cleanUpZombieSessions(Instant zombieThreshold, Duration trailingBuffer) {
        List<HighlightEventEntity> zombies = repository.findZombieSessions(zombieThreshold);
        List<String> finishedStreamIds = new ArrayList<>(zombies.size());

        for (HighlightEventEntity session : zombies) {
            Instant finalEndTime = session.getLastPeakTime().plus(trailingBuffer);
            long lastOffset = session.getLastPeakOffset() != null ? session.getLastPeakOffset() : 0L;
            long endOffset = lastOffset + trailingBuffer.toMillis();

            session.finish(finalEndTime, endOffset);
            finishedStreamIds.add(session.getStreamId());
            log.info("[Session-Cleanup] 방치된 좀비 하이라이트 세션 강제 종료 Stream: {}", session.getStreamId());
        }
        return finishedStreamIds;
    }

    private void extendSession(AnalysisSignal signal, HighlightEventEntity session) {
        long safeOffset = signal.offsetMs() != null ? signal.offsetMs() : 0L;
        session.updatePeakFirepower(signal.firepower());
        session.updateLastPeak(signal.timestamp(), safeOffset);
        log.info("[Session-Extend] 하이라이트 세션 연장 Stream: {}, Peak: {}", signal.streamId(), signal.firepower());
    }

    private void startNewSession(AnalysisSignal signal, Duration leadingBuffer) {
        long safeOffset = signal.offsetMs() != null ? signal.offsetMs() : 0L;
        long startTimeOffset = Math.max(0L, safeOffset - leadingBuffer.toMillis());
        Instant streamStartedAt = signal.offsetMs() != null
            ? signal.timestamp().minusMillis(signal.offsetMs())
            : signal.timestamp();
        Instant adjustedStart = streamStartedAt.plusMillis(startTimeOffset);

        HighlightEventEntity newSession = new HighlightEventEntity(
            signal.streamId(),
            signal.sessionId(),
            adjustedStart,
            startTimeOffset,
            signal.timestamp(),
            safeOffset,
            signal.firepower()
        );

        repository.save(newSession);
        log.info("[Session-Start] 하이라이트 세션 시작 Stream: {}, StartAt: {}", signal.streamId(), adjustedStart);
    }

    private void finishSession(AnalysisSignal signal, HighlightEventEntity session, Duration trailingBuffer) {
        Instant finalEndTime = session.getLastPeakTime().plus(trailingBuffer);
        long lastOffset = session.getLastPeakOffset() != null ? session.getLastPeakOffset() : 0L;
        long endTimeOffset = lastOffset + trailingBuffer.toMillis();
        session.finish(finalEndTime, endTimeOffset);

        log.info("[Session-Finish] 하이라이트 세션 종료 Stream:{}, Duration: {}",
            signal.streamId(), Duration.between(session.getStartTime(), finalEndTime));
    }
}
