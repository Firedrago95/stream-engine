package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import io.slice.stream.apiserver.stream.fake.FakeAnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisSignalBufferServiceTest {

    private FakeStringRedisTemplate redisTemplate;
    private FakeAnalysisSignalSummaryJdbcRepository summaryRepository;
    private AnalysisSignalBufferService bufferService;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        summaryRepository = new FakeAnalysisSignalSummaryJdbcRepository();
        bufferService = new AnalysisSignalBufferService(redisTemplate, summaryRepository);
    }

    @Test
    @DisplayName("신호 목록을 Redis List 버퍼에 정상 적재한다")
    void shouldBufferSignalsIntoRedis() {
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream-1", "session-1", "NORMAL", now, 100L, 1000L),
            new AnalysisSignal("stream-1", "session-1", "NORMAL", now.plusSeconds(3), 200L, 4000L)
        );

        bufferService.bufferSignals(signals);

        List<String> buffered = redisTemplate.opsForList().range("analysis:signals:buffer", 0, -1);
        assertThat(buffered).hasSize(2);
    }

    @Test
    @DisplayName("버퍼가 비어있을 때 플러시하면 0을 반환하고 저장을 건너뛴다")
    void shouldReturnZeroWhenBufferIsEmpty() {
        int persistedCount = bufferService.flushAndPersist();

        assertThat(persistedCount).isZero();
        assertThat(summaryRepository.getStorage()).isEmpty();
    }

    @Test
    @DisplayName("동일 1분 구간 내의 신호들을 그룹핑하여 평균 및 최대 화력으로 사전 집계하여 저장한다")
    void shouldAggregateAndPersistSummariesInOneMinuteWindow() {
        Instant t1 = Instant.parse("2026-10-15T12:00:10Z");
        Instant t2 = Instant.parse("2026-10-15T12:00:20Z");
        Instant t3 = Instant.parse("2026-10-15T12:00:40Z");

        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream-1", "session-1", "NORMAL", t1, 100L, 10_000L),
            new AnalysisSignal("stream-1", "session-1", "NORMAL", t2, 300L, 20_000L),
            new AnalysisSignal("stream-1", "session-1", "NORMAL", t3, 200L, 40_000L)
        );

        bufferService.bufferSignals(signals);

        int persistedCount = bufferService.flushAndPersist();

        assertThat(persistedCount).isEqualTo(1);
        List<AnalysisSignalSummaryDto> stored = summaryRepository.getStorage();
        assertThat(stored).hasSize(1);

        AnalysisSignalSummaryDto summary = stored.get(0);
        assertThat(summary.streamId()).isEqualTo("stream-1");
        assertThat(summary.sessionId()).isEqualTo("session-1");
        assertThat(summary.status()).isEqualTo("NORMAL");
        assertThat(summary.firepowerAvg()).isEqualTo(200L);
        assertThat(summary.firepowerMax()).isEqualTo(300L);
        assertThat(summary.offsetMs()).isZero();
        assertThat(summary.timestampMinute()).isEqualTo(t1);
    }

    @Test
    @DisplayName("서로 다른 분(minute) 구간 및 다른 세션의 신호는 분리하여 각각 집계한다")
    void shouldSeparateSummariesBySessionAndMinuteBucket() {
        Instant minute0 = Instant.parse("2026-10-15T12:00:10Z");
        Instant minute1 = Instant.parse("2026-10-15T12:01:10Z");

        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream-1", "session-1", "NORMAL", minute0, 100L, 10_000L),
            new AnalysisSignal("stream-1", "session-1", "NORMAL", minute1, 200L, 70_000L),
            new AnalysisSignal("stream-2", "session-2", "PEAK", minute0, 500L, 15_000L)
        );

        bufferService.bufferSignals(signals);

        int persistedCount = bufferService.flushAndPersist();

        assertThat(persistedCount).isEqualTo(3);
        List<AnalysisSignalSummaryDto> stored = summaryRepository.getStorage();
        assertThat(stored).hasSize(3);
    }
}
