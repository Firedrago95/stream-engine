package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.application.service.HighlightSessionService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightZombieSessionSchedulerTest {

    @Mock
    private HighlightSessionService highlightSessionService;

    @InjectMocks
    private HighlightZombieSessionScheduler scheduler;

    @Test
    void 정기_스케줄러는_좀비_하이라이트_세션_정리_메서드를_호출한다() {
        scheduler.scheduleZombieSessionCleanup();

        verify(highlightSessionService).cleanUpZombieSessions();
    }
}
