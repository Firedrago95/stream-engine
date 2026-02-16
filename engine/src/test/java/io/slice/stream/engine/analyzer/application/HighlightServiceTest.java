package io.slice.stream.engine.analyzer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
        lenient().when(clock.instant()).thenReturn(FIXED_NOW);
        lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());

        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(virtualThreadExecutor).execute(any(Runnable.class));
    }

    @Test
    void WAITING이_아닌_상태인_NORMAL과_PEAK는_모두_전송되어야_한다() {
        // given
        List<String> streamIds = List.of("stream-normal", "stream-peak", "stream-waiting");
        when(streamProvider.getActiveStreamIds()).thenReturn(streamIds);

        when(detector.detect("stream-normal")).thenReturn(ChatFirepowerStatus.NORMAL);
        when(detector.detect("stream-peak")).thenReturn(ChatFirepowerStatus.PEAK);
        when(detector.detect("stream-waiting")).thenReturn(ChatFirepowerStatus.WAITING);

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
        when(detector.detect(anyString())).thenReturn(ChatFirepowerStatus.WAITING);

        // when
        highlightService.monitorHighlights();

        // then
        verify(signalClient, never()).send(anyList());
    }
}
