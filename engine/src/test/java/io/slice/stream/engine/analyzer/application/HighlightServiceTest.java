package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.analyzer.domain.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.AnalysisSignal;
import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.DetectionResult;
import io.slice.stream.engine.analyzer.domain.HighlightDetector;
import io.slice.stream.engine.analyzer.domain.HighlightSignalClient;
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

    @Mock
    private ActiveStreamProvider streamProvider;
    @Mock
    private HighlightDetector detector;
    @Mock
    private HighlightSignalClient signalClient;
    @Mock
    private ExecutorService virtualThreadExecutor;
    @Mock
    private Clock clock;

    @InjectMocks
    private HighlightService highlightService;

    @BeforeEach
    void setUp() {
        // ExecutorService가 비동기가 아닌 동기식으로 즉시 실행되도록 설정하여 테스트를 용이하게 함
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(virtualThreadExecutor).execute(any(Runnable.class));
    }

    @Test
    void WAITING이_아닌_상태인_NORMAL과_PEAK는_모두_전송되어야_한다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        List<String> streamIds = List.of("stream-normal", "stream-peak", "stream-waiting");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream-normal")).thenReturn(new DetectionResult(ChatFirepowerStatus.NORMAL, 10L)); // firepower 추가
        when(detector.detect("stream-peak")).thenReturn(new DetectionResult(ChatFirepowerStatus.PEAK, 100L)); // firepower 추가
        when(detector.detect("stream-waiting")).thenReturn(DetectionResult.waiting()); // DetectionResult.waiting() 사용

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();

        assertAll(
            () -> assertThat(capturedSignals).hasSize(2),
            () -> assertThat(capturedSignals)
                .extracting(AnalysisSignal::status)
                .containsExactlyInAnyOrder("NORMAL", "PEAK")
                .doesNotContain("WAITING"),
            () -> assertThat(capturedSignals)
                .extracting(AnalysisSignal::streamId)
                .containsExactlyInAnyOrder("stream-normal", "stream-peak")
        );
    }

    @Test
    void 분석_가능한_신호가_하나도_없으면_클라이언트를_호출하지_않는다() {
        // given
        when(streamProvider.getActiveStreamIds()).thenReturn(List.of("stream-waiting1", "stream-waiting2"));
        when(detector.detect(anyString())).thenReturn(DetectionResult.waiting()); // DetectionResult.waiting() 사용

        // when
        highlightService.monitorHighlights();

        // then
        verify(signalClient, never()).send(anyList());
    }

    @Test
    void 특정_스트림_분석_중_예외가_발생해도_나머지_스트림은_정상_처리되어야_한다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        List<String> streamIds = List.of("stream-normal", "stream-exception", "stream-peak");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream-normal")).thenReturn(new DetectionResult(ChatFirepowerStatus.NORMAL, 20L)); // firepower 추가
        when(detector.detect("stream-exception")).thenThrow(new RuntimeException("Detector temporary error"));
        when(detector.detect("stream-peak")).thenReturn(new DetectionResult(ChatFirepowerStatus.PEAK, 200L)); // firepower 추가

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();
        assertThat(capturedSignals).hasSize(2);
        assertThat(capturedSignals)
            .extracting(AnalysisSignal::streamId)
            .containsExactlyInAnyOrder("stream-normal", "stream-peak");
    }

    private void assertAll(Runnable... assertions) {
        for (Runnable runnable : assertions) {
            runnable.run();
        }
    }
}
