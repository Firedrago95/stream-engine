package io.slice.stream.apiserver.streamer.application.scheduler;

import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
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

    @InjectMocks
    private StreamerLeaderboardScheduler scheduler;

    @Test
    void 정기_스케줄러는_리더보드_정산을_수행한다() {
        scheduler.scheduleDailyLeaderboardCalculation();

        verify(leaderboardQueryService).refreshDailyLeaderboard();
    }
}

