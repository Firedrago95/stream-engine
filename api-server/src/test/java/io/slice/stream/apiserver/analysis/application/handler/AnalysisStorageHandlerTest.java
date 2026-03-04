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
        AnalysisSignal signal = AnalysisSignal.of("test-stream", "NORMAL", Instant.now(), 100L);

        // when
        analysisStorageHandler.handleAnalysisSignal(signal);

        // then
        verify(analysisRepository).save(signal);
        verify(eventPublisher).publishEvent(any(SignalSavedEvent.class));
    }
}
