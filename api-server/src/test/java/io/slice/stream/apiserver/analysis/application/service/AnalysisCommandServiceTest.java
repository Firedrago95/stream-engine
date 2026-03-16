package io.slice.stream.apiserver.analysis.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
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
        // given
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream1", "sessionId", "PEAK", Instant.now(), 20L, 1000L),
            new AnalysisSignal("stream2", "sessionId", "NORMAL", Instant.now(), 5L, 2000L)
        );

        when(streamSessionService.getOrCreateActiveSession(anyString(), any(Instant.class)))
            .thenReturn("test-session-id");

        // when
        analysisCommandService.processSignals(signals);

        // then
        verify(eventPublisher, times(2)).publishEvent(any(SignalSavedEvent.class));
    }

    @Test
    void 빈_신호_리스트를_받으면_이벤트를_발행하지_않는다() {
        // given
        List<AnalysisSignal> signals = List.of();

        // when
        analysisCommandService.processSignals(signals);

        // then
        verify(eventPublisher, times(0)).publishEvent(any());
    }
}
