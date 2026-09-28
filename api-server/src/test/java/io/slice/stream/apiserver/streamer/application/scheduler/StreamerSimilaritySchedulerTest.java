package io.slice.stream.apiserver.streamer.application.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.streamer.application.StreamerSimilarityBatchService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerSimilaritySchedulerTest {

    @Mock
    private StreamerSimilarityBatchService similarityBatchService;

    private StreamerSimilarityScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new StreamerSimilarityScheduler(similarityBatchService);
    }

    @Test
    void 스케줄러_호출_시_당일_기준으로_배치_서비스를_호출한다() {
        scheduler.runSimilarityBatch();

        verify(similarityBatchService, times(1)).executeBatch(any(LocalDate.class));
    }

    @Test
    void 배치_서비스_실행_중_예외가_발생해도_스케줄러가_정상_종료된다() {
        doThrow(new RuntimeException("DB 연결 실패"))
            .when(similarityBatchService).executeBatch(any(LocalDate.class));

        scheduler.runSimilarityBatch();

        verify(similarityBatchService, times(1)).executeBatch(any(LocalDate.class));
    }
}
