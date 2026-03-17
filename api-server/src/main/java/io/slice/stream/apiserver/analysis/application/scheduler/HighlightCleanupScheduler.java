package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.global.config.HighlightProperties;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final JpaHighlightEventRepository highlightRepository;
    private final HighlightProperties properties;

    // 매일 새벽 6시 정각에 실행
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    @Transactional
    public void cleanupOldHighlights() {
        // 현재 시간 기준 24시간 전 시점 계산
        Instant twentyFourHoursAgo = Instant.now().minus(properties.cleanupGraceHours(), ChronoUnit.HOURS);

        // 종료된 세션 중, 종료 시간이 24시간을 넘긴 세션들만 조회
        sessionRepository.findFinishedSessionsOlderThan(twentyFourHoursAgo).forEach(session -> {
            int deletedCount = highlightRepository.deleteExceptTop(session.getSessionId(), properties.cleanupRetentionLimit());
            if (deletedCount > 0) {
                log.info("[Cleanup] 세션 {} 의 데이터 {}개 정리 완료 (Top 10 유지)",
                    session.getSessionId(), deletedCount);
            }
        });
    }
}
