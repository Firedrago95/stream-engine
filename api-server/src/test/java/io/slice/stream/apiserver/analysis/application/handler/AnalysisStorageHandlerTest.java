package io.slice.stream.apiserver.analysis.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.application.service.AnalysisSignalBufferService;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.domain.event.SignalSavedEvent;
import io.slice.stream.apiserver.analysis.domain.event.SignalsReceivedEvent;
import io.slice.stream.apiserver.stream.fake.FakeAnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.stream.fake.FakeApplicationEventPublisher;
import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisStorageHandlerTest {

    private FakeStringRedisTemplate redisTemplate;
    private FakeAnalysisSignalSummaryJdbcRepository summaryRepository;
    private FakeApplicationEventPublisher eventPublisher;
    private AnalysisSignalBufferService bufferService;
    private AnalysisStorageHandler analysisStorageHandler;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        summaryRepository = new FakeAnalysisSignalSummaryJdbcRepository();
        eventPublisher = new FakeApplicationEventPublisher();
        bufferService = new AnalysisSignalBufferService(redisTemplate, summaryRepository);
        analysisStorageHandler = new AnalysisStorageHandler(bufferService, eventPublisher);
    }

    @Test
    @DisplayName("단건 분석 신호 수신 시 Redis 버퍼에 적재되고 SignalSavedEvent가 발행된다")
    void shouldBufferSignalAndPublishEventOnSingleSignal() {
        AnalysisSignal signal = AnalysisSignal.of("test-stream", "sessionId", "NORMAL", Instant.now(), 100L, 1000L);

        analysisStorageHandler.handleAnalysisSignal(signal);

        List<String> buffered = redisTemplate.opsForList().range("analysis:signals:buffer", 0, -1);
        assertThat(buffered).hasSize(1);

        List<SignalSavedEvent> events = eventPublisher.getEventsOfType(SignalSavedEvent.class);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).signal()).isEqualTo(signal);
    }

    @Test
    @DisplayName("일괄 신호 수신 시 Redis 버퍼에 전건 적재되고 각 신호별 SignalSavedEvent가 발행된다")
    void shouldBufferAllSignalsAndPublishEventsOnBatchSignals() {
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of("stream1", "sessionId1", "NORMAL", Instant.now(), 100L, 1000L),
            AnalysisSignal.of("stream2", "sessionId2", "PEAK", Instant.now(), 500L, 2000L)
        );
        SignalsReceivedEvent event = new SignalsReceivedEvent(signals);

        analysisStorageHandler.handleSignalsReceived(event);

        List<String> buffered = redisTemplate.opsForList().range("analysis:signals:buffer", 0, -1);
        assertThat(buffered).hasSize(2);

        List<SignalSavedEvent> events = eventPublisher.getEventsOfType(SignalSavedEvent.class);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).signal()).isEqualTo(signals.get(0));
        assertThat(events.get(1).signal()).isEqualTo(signals.get(1));
    }
}
