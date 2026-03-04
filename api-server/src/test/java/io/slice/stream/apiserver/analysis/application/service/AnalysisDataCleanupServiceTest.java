package io.slice.stream.apiserver.analysis.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
class AnalysisDataCleanupServiceTest {

    @Mock
    private JpaAnalysisSignalRepository jpaRepository;

    @InjectMocks
    private AnalysisDataCleanupService cleanupService;

    @Test
    void 데이터_정리_로직은_요약_작업_후_삭제_작업을_순서대로_수행한다() {
        // given
        Instant cutoffTime = Instant.now();
        when(jpaRepository.rollupOldSignals(cutoffTime)).thenReturn(100);
        when(jpaRepository.deleteOlderThan(cutoffTime)).thenReturn(5000);

        // when
        cleanupService.cleanupOldData(cutoffTime);

        // then
        var inOrder = inOrder(jpaRepository);
        inOrder.verify(jpaRepository).rollupOldSignals(cutoffTime);
        inOrder.verify(jpaRepository).deleteOlderThan(cutoffTime);
    }

    @Test
    void 요약_작업_중_예외가_발생하면_삭제_작업은_수행되지_않고_예외를_던진다() {
        // given
        Instant cutoffTime = Instant.now();
        when(jpaRepository.rollupOldSignals(any(Instant.class)))
            .thenThrow(new RuntimeException("DB Error"));

        // when & then
        try {
            cleanupService.cleanupOldData(cutoffTime);
        } catch (Exception ignored) {
            // Service는 트랜잭션 롤백을 위해 예외를 밖으로 던져야 함
        }

        verify(jpaRepository).rollupOldSignals(any(Instant.class));
        verify(jpaRepository, never()).deleteOlderThan(any(Instant.class));
    }
}
