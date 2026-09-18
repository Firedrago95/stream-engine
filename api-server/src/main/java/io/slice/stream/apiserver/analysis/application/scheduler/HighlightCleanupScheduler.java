package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import java.time.Instant;
import java.time.LocalDate;
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
    private final StreamerFollowerSnapshotRepository followerSnapshotRepository;
    private final HighlightProperties properties;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldHighlights() {
        trimOldSessionHighlights();
        purgeExpiredHighlights();
        purgeExpiredSessions();
        purgeExpiredFollowerSnapshots();
    }

    private void trimOldSessionHighlights() {
        Instant twentyFourHoursAgo = Instant.now().minus(properties.cleanupGraceHours(), ChronoUnit.HOURS);

        sessionRepository.findFinishedSessionsOlderThan(twentyFourHoursAgo).forEach(session -> {
            int deletedCount = highlightRepository.deleteExceptTop(session.getSessionId(), properties.cleanupRetentionLimit());
            if (deletedCount > 0) {
                log.info("[Cleanup] 세션 {} 데이터 {}개 정리 완료 (Top 10 유지)",
                    session.getSessionId(), deletedCount);
            }
        });
    }

    private void purgeExpiredHighlights() {
        Instant highlightExpiredThreshold = Instant.now().minus(properties.highlightRetentionDays(), ChronoUnit.DAYS);
        List<StreamSessionEntity> highlightExpiredSessions = sessionRepository.findFinishedSessionsOlderThan(highlightExpiredThreshold);

        if (!highlightExpiredSessions.isEmpty()) {
            List<String> expiredSessionIds = highlightExpiredSessions.stream()
                .map(StreamSessionEntity::getSessionId)
                .toList();

            highlightRepository.deleteAllBySessionIds(expiredSessionIds);

            log.info("[Cleanup] {}일 이상 지난 만료 하이라이트 영상 클립 영구 삭제 완료 (총 {}개 세션)",
                properties.highlightRetentionDays(), expiredSessionIds.size());
        }
    }

    private void purgeExpiredSessions() {
        Instant sessionExpiredThreshold = Instant.now().minus(properties.sessionRetentionDays(), ChronoUnit.DAYS);
        List<StreamSessionEntity> sessionExpiredSessions = sessionRepository.findFinishedSessionsOlderThan(sessionExpiredThreshold);
        if (!sessionExpiredSessions.isEmpty()) {
            List<String> expiredSessionIds = sessionExpiredSessions.stream()
                .map(StreamSessionEntity::getSessionId)
                .toList();

            segmentRepository.deleteAllBySessionIds(expiredSessionIds);
            int deletedSessionCount = sessionRepository.deleteExpiredSessions(sessionExpiredThreshold);

            log.info("[Cleanup] {}일(1년) 이상 지난 만료 방송 세션 {}건 및 카테고리 구간 영구 삭제 완료",
                properties.sessionRetentionDays(), deletedSessionCount);
        }
    }

    private void purgeExpiredFollowerSnapshots() {
        LocalDate snapshotExpiredThreshold = LocalDate.now().minusDays(properties.sessionRetentionDays());
        int deletedSnapshotCount = followerSnapshotRepository.deleteExpiredSnapshots(snapshotExpiredThreshold);
        if (deletedSnapshotCount > 0) {
            log.info("[Cleanup] {}일(1년) 이상 지난 만료 팔로워 스냅샷 {}건 영구 삭제 완료",
                properties.sessionRetentionDays(), deletedSnapshotCount);
        }
    }
}
