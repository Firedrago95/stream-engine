package io.slice.stream.apiserver.analysis.application.scheduler;

import io.slice.stream.apiserver.analysis.application.service.AnalysisDataCleanupService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component; // Service보다는 Component가 적절합니다.

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisDataCleanupScheduler {

    private static final int CUTOFF_DAY = 3;

    private final AnalysisDataCleanupService cleanupService;

    @Scheduled(cron = "0 0 7 * * *")
    public void runDataCleanUp() {
        log.info("[Cleanup] 3일 주기 데이터 요약 및 정리 작업을 시작합니다.");

        Instant cutoffTime = Instant.now().minus(CUTOFF_DAY, ChronoUnit.DAYS);

        try {
            cleanupService.cleanupOldData(cutoffTime);

            log.info("[Cleanup] 데이터 정리 작업이 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            log.error("[Cleanup] 작업 중 오류 발생. (데이터는 안전하게 롤백되었습니다.)", e);
        }
    }
}
