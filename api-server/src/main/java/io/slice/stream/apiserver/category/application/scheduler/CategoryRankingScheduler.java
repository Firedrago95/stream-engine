package io.slice.stream.apiserver.category.application.scheduler;

import io.slice.stream.apiserver.category.application.CategoryRankingBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryRankingScheduler {

    private final CategoryRankingBatchService batchService;

    @Scheduled(cron = "0 0 6 * * MON", zone = "Asia/Seoul")
    public void scheduleWeeklyRankingUpdate() {
        log.info("[Scheduler] 매주 월요일 06:00 정기 카테고리 랭킹 배치 실행");
        batchService.refreshWeeklyRanking();
    }
}
