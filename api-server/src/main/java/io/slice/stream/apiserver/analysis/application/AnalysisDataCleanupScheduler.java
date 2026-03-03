package io.slice.stream.apiserver.analysis.application;

import io.slice.stream.apiserver.analysis.infrastructure.JpaAnalysisSignalRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class AnalysisDataCleanupScheduler {

    private static final int CUTOFF_DAY = 3;

    private final JpaAnalysisSignalRepository jpaRepository;

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void runDataCleanUp() {
        log.info("[Cleanup] 3일 주기 데이터 요약 및 정리 작업을 시작합니다.");

        Instant cutoffTime = Instant.now().minus(CUTOFF_DAY, ChronoUnit.DAYS);

        try {
            int summaryCount = jpaRepository.rollupOldSignals(cutoffTime);
            log.info("[Cleanup] {} 건의 데이터가 요약 테이블에 반영되었습니다.", summaryCount);

            int deleteCount = jpaRepository.deleteOlderThan(cutoffTime);
            log.info("[Cleanup] 3일이 지난 원본 데이터 {} 건이 삭제되었습니다.", deleteCount);
        } catch (Exception e) {
            log.error("[Cleanup] 작업 중 오류 발생", e);
        }
    }
}
