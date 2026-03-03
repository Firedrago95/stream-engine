package io.slice.stream.apiserver.analysis.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.infrastructure.JpaAnalysisSignalRepository;
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
    private JpaAnalysisSignalRepository jpaRepository;

    @InjectMocks
    private AnalysisDataCleanupScheduler cleanupScheduler;

    @Test
    void 데이터_정리_작업이_실행되면_요약_후_삭제가_실행된다() {
        // given
        when(jpaRepository.rollupOldSignals(any(Instant.class))).thenReturn(100);
        when(jpaRepository.deleteOlderThan(any(Instant.class))).thenReturn(5000);

        // when
        cleanupScheduler.runDataCleanUp();

        // then
        verify(jpaRepository, times(1)).rollupOldSignals(any(Instant.class));

        verify(jpaRepository, times(1)).deleteOlderThan(any(Instant.class));

        var inOrder = inOrder(jpaRepository);
        inOrder.verify(jpaRepository).rollupOldSignals(any(Instant.class));
        inOrder.verify(jpaRepository).deleteOlderThan(any(Instant.class));
    }

    @Test
    void 요약_작업_중_예외가_발생하면_삭제_작업은_실행되지_않는다() {
        // given
        when(jpaRepository.rollupOldSignals(any(Instant.class)))
            .thenThrow(new RuntimeException("DB Connection Error"));

        // when
        cleanupScheduler.runDataCleanUp();

        // then
        verify(jpaRepository, times(1)).rollupOldSignals(any(Instant.class));
        verify(jpaRepository, never()).deleteOlderThan(any(Instant.class));
    }
}
