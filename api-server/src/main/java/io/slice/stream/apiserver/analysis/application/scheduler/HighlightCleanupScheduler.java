package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class HighlightCleanupScheduler {

    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaHighlightEventRepository highlightRepository;
    private final HighlightProperties properties;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldHighlights() {
        Instant twentyFourHoursAgo = Instant.now().minus(properties.cleanupGraceHours(), ChronoUnit.HOURS);

        sessionRepository.findFinishedSessionsOlderThan(twentyFourHoursAgo).forEach(session -> {
            int deletedCount = highlightRepository.deleteExceptTop(session.getSessionId(), properties.cleanupRetentionLimit());
            if (deletedCount > 0) {
                log.info("[Cleanup] 세션 {} 데이터 {}개 정리 완료 (Top 10 유지)",
                    session.getSessionId(), deletedCount);
            }
        });

        Instant expiredThreshold = Instant.now().minus(properties.sessionRetentionDays(), ChronoUnit.DAYS);
        List<StreamSessionEntity> expiredSessions = sessionRepository.findFinishedSessionsOlderThan(expiredThreshold);

        if (!expiredSessions.isEmpty()) {
            List<String> expiredSessionIds = expiredSessions.stream()
                .map(StreamSessionEntity::getSessionId)
                .toList();

            highlightRepository.deleteAllBySessionIds(expiredSessionIds);
            segmentRepository.deleteAllBySessionIds(expiredSessionIds);
            int deletedSessionCount = sessionRepository.deleteExpiredSessions(expiredThreshold);

            log.info("[Cleanup] {}일 이상 지난 만료 세션 {}건 및 연관 하이라이트 영구 삭제 완료",
                properties.sessionRetentionDays(), deletedSessionCount);
        }
    }
}
