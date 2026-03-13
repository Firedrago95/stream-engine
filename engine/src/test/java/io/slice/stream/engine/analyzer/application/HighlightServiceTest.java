package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
    void WAITING_상태는_NORMAL로_변환되어_전송되고_나머지는_상태_그대로_전송된다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        List<String> streamIds = List.of("stream-normal", "stream-peak", "stream-waiting");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream-normal")).thenReturn(new DetectionResult(ChatFirepowerStatus.NORMAL, 10L));
        when(detector.detect("stream-peak")).thenReturn(new DetectionResult(ChatFirepowerStatus.PEAK, 100L));
        // WAITING 상태 주입
        when(detector.detect("stream-waiting")).thenReturn(DetectionResult.waiting());

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();

        assertAll(
            // 3개의 스트림이 모두 버려지지 않고 전송되어야 함
            () -> assertThat(capturedSignals).hasSize(3),
            // WAITING이 NORMAL로 둔갑했으므로 NORMAL 2개, PEAK 1개여야 함
            () -> assertThat(capturedSignals)
                .extracting(AnalysisSignal::status)
                .containsExactlyInAnyOrder("NORMAL", "PEAK", "NORMAL")
                .doesNotContain("WAITING"),
            () -> assertThat(capturedSignals)
                .extracting(AnalysisSignal::streamId)
                .containsExactlyInAnyOrder("stream-normal", "stream-peak", "stream-waiting")
        );
    }

    @Test
    void 분석_결과가_모두_WAITING이어도_차트_렌더링을_위해_NORMAL로_변환하여_클라이언트를_호출한다() {
        // given
        // WAITING이어도 AnalysisSignal 객체를 만들기 위해 clock이 호출되므로 Mock 설정 추가
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(streamProvider.getActiveStreamIds()).thenReturn(List.of("stream-waiting1", "stream-waiting2"));
        when(detector.detect(anyString())).thenReturn(DetectionResult.waiting());

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);

        // 예전엔 never()였지만, 이제는 전송해야 성공!
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();
        assertAll(
            () -> assertThat(capturedSignals).hasSize(2),
            () -> assertThat(capturedSignals)
                .extracting(AnalysisSignal::status)
                .containsOnly("NORMAL") // 💡 모두 NORMAL로 변환되었는지 검증
        );
    }

    @Test
    void 특정_스트림_분석_중_예외가_발생해도_나머지_스트림은_정상_처리되어야_한다() {
        // given
        when(clock.instant()).thenReturn(FIXED_NOW);
        List<String> streamIds = List.of("stream-normal", "stream-exception", "stream-peak");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream-normal")).thenReturn(new DetectionResult(ChatFirepowerStatus.NORMAL, 20L));
        // 예외 발생 시나리오
        when(detector.detect("stream-exception")).thenThrow(new RuntimeException("Detector temporary error"));
        when(detector.detect("stream-peak")).thenReturn(new DetectionResult(ChatFirepowerStatus.PEAK, 200L));

        // when
        highlightService.monitorHighlights();

        // then
        ArgumentCaptor<List<AnalysisSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(signalClient, times(1)).send(captor.capture());

        List<AnalysisSignal> capturedSignals = captor.getValue();

        // 예외가 발생한 스트림만 Optional.empty()로 걸러지고 나머지 2개는 정상 전송
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
