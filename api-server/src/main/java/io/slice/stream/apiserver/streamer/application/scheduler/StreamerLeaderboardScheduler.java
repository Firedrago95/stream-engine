package io.slice.stream.apiserver.streamer.application.scheduler;

import io.slice.stream.apiserver.streamer.application.StreamerDailyStatCommandService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamerLeaderboardScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final StreamerLeaderboardQueryService leaderboardQueryService;
    private final StreamerDailyStatCommandService dailyStatCommandService;

    @Scheduled(cron = "${streamer.leaderboard.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduleDailyLeaderboardCalculation() {
        log.info("[Leaderboard-Scheduler] 새벽 04:00 정기 스트리머 체급 리더보드 및 일별 통계 정산 시작");
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        dailyStatCommandService.recordActiveSessionsDailySnapshot(yesterday);
        leaderboardQueryService.refreshDailyLeaderboard();
        log.info("[Leaderboard-Scheduler] 새벽 04:00 정기 스트리머 체급 리더보드 및 일별 통계 정산 완료");
    }
}
