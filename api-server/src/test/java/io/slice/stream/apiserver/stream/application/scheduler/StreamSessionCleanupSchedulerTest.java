package io.slice.stream.apiserver.stream.application.scheduler;

import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.stream.application.StreamSessionService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionCleanupSchedulerTest {

    @Mock
    private StreamSessionService streamSessionService;

    @InjectMocks
    private StreamSessionCleanupScheduler scheduler;

    @Test
    void 정기_스케줄러는_오프라인_세션_정리_메서드를_호출한다() {
        scheduler.scheduleOfflineSessionCleanup();

        verify(streamSessionService).closeOfflineSessions();
    }
}
