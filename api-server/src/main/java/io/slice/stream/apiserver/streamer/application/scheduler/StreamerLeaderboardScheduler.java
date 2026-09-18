package io.slice.stream.apiserver.streamer.application.scheduler;

import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamerLeaderboardScheduler {

    private final StreamerLeaderboardQueryService leaderboardQueryService;

    @Scheduled(cron = "${streamer.leaderboard.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduleDailyLeaderboardCalculation() {
        log.info("[Leaderboard-Scheduler] 정기 스트리머 체급 리더보드 정산 시작");
        leaderboardQueryService.refreshDailyLeaderboard();
        log.info("[Leaderboard-Scheduler] 정기 스트리머 체급 리더보드 정산 완료");
    }
}

