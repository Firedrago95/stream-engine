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
        compressOldSessionHighlights();
        purgeExpiredHighlights();
        purgeExpiredSessions();
        purgeExpiredFollowerSnapshots();
    }

    private void compressOldSessionHighlights() {
        Instant compressionThreshold = Instant.now().minus(properties.cleanupGraceDays(), ChronoUnit.DAYS);
        int deletedCount = highlightRepository.compressOldHighlightsExceptTop(
            compressionThreshold,
            properties.cleanupRetentionLimit()
        );
        if (deletedCount > 0) {
            log.info("[하이라이트 압축] {}일 경과 세션의 하이라이트 중 상위 {}개 제외 {}건 벌크 정리 완료",
                properties.cleanupGraceDays(), properties.cleanupRetentionLimit(), deletedCount);
        }
    }

    private void purgeExpiredHighlights() {
        Instant highlightExpiredThreshold = Instant.now().minus(properties.highlightRetentionDays(), ChronoUnit.DAYS);
        int deletedCount = highlightRepository.deleteExpiredHighlights(highlightExpiredThreshold);
        if (deletedCount > 0) {
            log.info("[하이라이트 만료 삭제] {}일(1년) 경과 하이라이트 데이터 {}건 영구 삭제 완료",
                properties.highlightRetentionDays(), deletedCount);
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
