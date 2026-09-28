package io.slice.stream.apiserver.analysis.application.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.times;

import io.slice.stream.apiserver.analysis.domain.event.SignalsReceivedEvent;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AnalysisStorageHandlerTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AnalysisStorageHandler analysisStorageHandler;

    @Test
    void 이벤트가_방생하면_저장소_저장_메서드가_호출된다() {
        // given
        AnalysisSignal signal = AnalysisSignal.of("test-stream", "sessionId", "NORMAL", Instant.now(), 100L, 1000L);

        // when
        analysisStorageHandler.handleAnalysisSignal(signal);

        // then
        verify(analysisRepository).save(signal);
        verify(eventPublisher).publishEvent(any(SignalSavedEvent.class));
    }

    @Test
    void 일괄_신호_수신_이벤트가_발생하면_일괄_저장소_메서드가_호출되고_각_신호의_저장_완료_이벤트가_발행된다() {
        // given
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of("stream1", "sessionId1", "NORMAL", Instant.now(), 100L, 1000L),
            AnalysisSignal.of("stream2", "sessionId2", "PEAK", Instant.now(), 500L, 2000L)
        );
        SignalsReceivedEvent event = new SignalsReceivedEvent(signals);

        // when
        analysisStorageHandler.handleSignalsReceived(event);

        // then
        verify(analysisRepository).saveAll(signals);
        verify(eventPublisher, times(2)).publishEvent(any(SignalSavedEvent.class));
    }
}
