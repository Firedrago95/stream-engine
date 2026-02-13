package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.HighlightSignalClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    @Mock
    private ActiveStreamProvider streamProvider;
    @Mock
    private ExecutorService virtualThreadExecutor;
    @Mock
    private HighlightDetector detector;
    @Mock
    private HighlightSignalClient signalClient;
    @Mock
    private Clock clock;

    @InjectMocks
    private HighlightService highlightService;

    private static final Instant FIXED_NOW = Instant.parse("2026-02-13T10:00:00Z");

    @BeforeEach
    void setUp() {
        // Clock의 동작을 고정
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        // ExecutorService가 비동기가 아닌 동기식으로 즉시 실행되도록 설정하여 테스트를 용이하게 함
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(virtualThreadExecutor).execute(any(Runnable.class));
    }

    @Test
    void 활성화된_스트림에서_하이라이트_신호를_감지하여_전송한다() {
        // given
        List<String> streamIds = List.of("stream1", "stream2", "stream3");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream1")).thenReturn(ChatFirepowerStatus.PEAK);
        when(detector.detect("stream2")).thenReturn(ChatFirepowerStatus.WAITING);
        when(detector.detect("stream3")).thenThrow(new RuntimeException("Test Exception"));

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();
        assertThat(capturedSignals).hasSize(1);

        AnalysisSignal signal = capturedSignals.getFirst();
        assertThat(signal.streamId()).isEqualTo("stream1");
        assertThat(signal.status()).isEqualTo(ChatFirepowerStatus.PEAK.name());
        assertThat(signal.timestamp()).isEqualTo(FIXED_NOW);
    }

    @Test
    void 하이라이트_신호가_없으면_클라이언트를_호출하지_않는다() {
        // given
        List<String> streamIds = List.of("stream1", "stream2");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream1")).thenReturn(ChatFirepowerStatus.WAITING);
        when(detector.detect("stream2")).thenReturn(ChatFirepowerStatus.NORMAL);

        // when
        highlightService.monitorHighlights();

        // then
        verify(signalClient, never()).send(anyList());
    }

    @Test
    void 활성화된_스트림이_없으면_아무_작업도_하지_않는다() {
        // given
        when(streamProvider.getActiveStreamIds()).thenReturn(List.of());

        // when
        highlightService.monitorHighlights();

        // then
        verify(detector, never()).detect(any());
        verify(signalClient, never()).send(anyList());
    }
}
