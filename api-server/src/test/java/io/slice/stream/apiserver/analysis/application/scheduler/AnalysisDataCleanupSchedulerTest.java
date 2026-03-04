package io.slice.stream.apiserver.analysis.application.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.application.service.AnalysisDataCleanupService;
import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
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
    void 서비스_로직에서_예외가_발생하면_CLEANUP_FAILED_에러코드를_가진_BusinessException을_던진다() {
        // given
        String errorMessage = "DB Connection Timeout";
        doThrow(new RuntimeException(errorMessage))
            .when(cleanupService).cleanupOldData(any(Instant.class));

        // when & then
        assertThatThrownBy(() -> cleanupScheduler.runDataCleanUp())
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> {
                BusinessException be = (BusinessException) e;
                assertThat(be.getErrorCode()).isEqualTo(ErrorCode.CLEANUP_FAILED);
                assertThat(be.getMessage()).contains(errorMessage);
            });

        verify(cleanupService, times(1)).cleanupOldData(any(Instant.class));
    }
}
