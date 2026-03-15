package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.detection.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.signal.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.signal.HighlightSignalClient;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import io.slice.stream.engine.core.model.StreamTarget;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-02-13T10:00:00Z");

    @Mock private ActiveStreamProvider streamProvider;
    @Mock private ExecutorService virtualThreadExecutor;
    @Mock private HighlightSignalClient signalClient;
    @Mock private Clock clock;

    @Mock private StreamTierManager tierManager;
    @Mock private ChatRoomAggregationRepository repository;
    @Mock private HighlightDetector detector;
    @Mock private HighlightEngineProperties props;

    @InjectMocks
    private HighlightService highlightService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(virtualThreadExecutor).execute(any(Runnable.class));
    }

    @Test
    void 모든_파이프라인이_성공적으로_동작하여_PEAK_신호를_전송한다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(props.fetchBufferSeconds()).thenReturn(15); // YAML 설정값 Mocking

        StreamTarget target = new StreamTarget("stream1", "침착맨", "chat1", 1L, "title", 1000, "url", "게임");
        when(streamProvider.getActiveStreamTargets()).thenReturn(List.of(target));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder().tier(StreamTier.GROUP_A).windowSeconds(60).build();
        when(tierManager.getTierInfo("stream1", 1000)).thenReturn(mockTierInfo);

        List<Long> mockDeltas = List.of(1L, 2L, 50L);
        when(repository.getFirepowerDeltas(eq("stream1"), any(Instant.class), eq(FIXED_NOW)))
            .thenReturn(mockDeltas);

        when(detector.detect("stream1", mockDeltas, mockTierInfo))
            .thenReturn(new DetectionResult(ChatFirepowerStatus.PEAK, 50L));

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();
        assertThat(capturedSignals).hasSize(1);
        assertThat(capturedSignals.get(0).status()).isEqualTo("PEAK");
        assertThat(capturedSignals.get(0).firepower()).isEqualTo(50L);
    }

    @Test
    void WAITING_상태는_NORMAL로_둔갑하여_차트_렌더링용으로_전송된다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(props.fetchBufferSeconds()).thenReturn(15);

        StreamTarget target = new StreamTarget("stream-wait", "하꼬방", "chat1", 1L, "title", 10, "url", "소통");
        when(streamProvider.getActiveStreamTargets()).thenReturn(List.of(target));

        StreamTierInfo mockTierInfo = StreamTierInfo.builder().tier(StreamTier.GROUP_B).windowSeconds(120).build();
        when(tierManager.getTierInfo(anyString(), anyInt())).thenReturn(mockTierInfo);
        when(repository.getFirepowerDeltas(anyString(), any(), any())).thenReturn(List.of(1L, 2L));

        // WAITING 판정 주입
        when(detector.detect(anyString(), any(), any())).thenReturn(DetectionResult.waiting());

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        assertThat(captor.getValue().get(0).status()).isEqualTo("NORMAL");
    }
}
