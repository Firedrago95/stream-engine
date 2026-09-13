package io.slice.stream.apiserver.analysis.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.stream.application.StreamSessionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisCommandServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private StreamSessionService streamSessionService;

    @InjectMocks
    private AnalysisCommandService analysisCommandService;

    @Test
    void 신호_리스트를_받으면_각_신호를_내부_이벤트로_발행한다() {
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "sessionId", "PEAK", Instant.now(), 20L, 1000L),
            new AnalysisSignal("stream2", "sessionId", "NORMAL", Instant.now(), 5L, 2000L)
        );

        when(streamSessionService.getOrCreateActiveSession(anyString(), anyString(), any(Instant.class)))
            .thenReturn("test-session-id");

        analysisCommandService.processSignals(signals);

        verify(eventPublisher, times(2)).publishEvent(any(AnalysisSignal.class));
    }

    @Test
    void 신호_처리시_치지직_원본_시작시각을_역산하여_세션_서비스에_전달한다() {
        Instant signalTime = Instant.parse("2026-02-13T10:30:00Z");
        long offsetMs = 1800000L;
        Instant expectedStartedAt = Instant.parse("2026-02-13T10:00:00Z");

        AnalysisSignal signal = new AnalysisSignal("stream1", "live1", "PEAK", signalTime, 50L, offsetMs);

        when(streamSessionService.getOrCreateActiveSession("stream1", "live1", expectedStartedAt))
            .thenReturn("session-1");

        analysisCommandService.processSignals(List.of(signal));

        verify(streamSessionService, times(1)).getOrCreateActiveSession("stream1", "live1", expectedStartedAt);
    }

    @Test
    void 빈_신호_리스트를_받으면_이벤트를_발행하지_않는다() {
        List<AnalysisSignal> signals = List.of();

        analysisCommandService.processSignals(signals);

        verify(eventPublisher, times(0)).publishEvent(any());
    }
}
