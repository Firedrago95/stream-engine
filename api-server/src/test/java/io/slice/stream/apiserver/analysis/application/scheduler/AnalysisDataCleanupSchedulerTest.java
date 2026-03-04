package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.application.service.AnalysisDataCleanupService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisDataCleanupSchedulerTest {

    @Mock
    private AnalysisDataCleanupService cleanupService;

    @InjectMocks
    private AnalysisDataCleanupScheduler cleanupScheduler;

    @Test
    void 스케줄러가_실행되면_정리_서비스를_호출한다() {
        // when
        cleanupScheduler.runDataCleanUp();

        // then
        verify(cleanupService, times(1)).cleanupOldData(any(Instant.class));
    }

    @Test
    void 서비스_로직에서_예외가_발생해도_스케줄러는_예외를_밖으로_던지지_않는다() {
        // given: 서비스에서 에러가 발생하는 상황
        doThrow(new RuntimeException("Service Failure"))
            .when(cleanupService).cleanupOldData(any(Instant.class));

        // when & then: 예외가 발생해도 runDataCleanUp 메서드는 정상 종료되어야 함 (assertDoesNotThrow)
        assertDoesNotThrow(() -> cleanupScheduler.runDataCleanUp());

        verify(cleanupService, times(1)).cleanupOldData(any(Instant.class));
    }
}
