package io.slice.stream.apiserver.streamer.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerLeaderboardSchedulerTest {

    @Mock
    private StreamerLeaderboardQueryService leaderboardQueryService;

    @Mock
    private StreamerDailyStatCommandService dailyStatCommandService;

    @InjectMocks
    private StreamerLeaderboardScheduler scheduler;

    @Test
    void 새벽_정기_스케줄러는_어제_일별_통계_스냅샷과_리더보드_정산을_순서대로_수행한다() {
        scheduler.scheduleDailyLeaderboardCalculation();

        verify(dailyStatCommandService).recordActiveSessionsDailySnapshot(any(LocalDate.class));
        verify(leaderboardQueryService).refreshDailyLeaderboard();
    }
}
