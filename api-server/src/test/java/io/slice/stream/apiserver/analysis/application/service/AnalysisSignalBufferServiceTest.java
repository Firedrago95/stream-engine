package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.AnalysisRedisKeyManager;
import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import io.slice.stream.apiserver.stream.fake.FakeAnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.stream.fake.FakeStringRedisTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisSignalBufferServiceTest {

    private FakeStringRedisTemplate redisTemplate;
    private FakeAnalysisSignalSummaryJdbcRepository summaryRepository;
    private AnalysisRedisKeyManager keyManager;
    private AnalysisSignalBufferService bufferService;

    @BeforeEach
    void setUp() {
        redisTemplate = new FakeStringRedisTemplate();
        summaryRepository = new FakeAnalysisSignalSummaryJdbcRepository();
        keyManager = new AnalysisRedisKeyManager();
        bufferService = new AnalysisSignalBufferService(redisTemplate, summaryRepository, keyManager);
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

        List<String> buffered = redisTemplate.opsForList().range(keyManager.getBufferKey(), 0, -1);
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
    @DisplayName("동일 1분 구간 내의 신호들을 그룹핑하여 평균 및 최대 화력으로 사전 집계하여 저장하고 Redis 키를 삭제한다")
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

        Set<String> remainingProcessingKeys = redisTemplate.keys(keyManager.getProcessingKeyPattern());
        assertThat(remainingProcessingKeys).isEmpty();
    }

    @Test
    @DisplayName("DB 저장 실패 시 Redis의 processing 키를 삭제하지 않고 온전히 보존한다")
    void shouldPreserveProcessingKeyWhenDbFails() {
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            new AnalysisSignal("stream-1", "session-1", "NORMAL", now, 100L, 10_000L)
        );
        bufferService.bufferSignals(signals);

        summaryRepository.setShouldFail(true);

        assertThatThrownBy(() -> bufferService.flushAndPersist())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("DB 일괄 저장 실패");

        Set<String> remainingProcessingKeys = redisTemplate.keys(keyManager.getProcessingKeyPattern());
        assertThat(remainingProcessingKeys).hasSize(1);
    }

    @Test
    @DisplayName("이전 플러시 실패로 남아있던 잔여 processing 키를 다음 플러시에서 먼저 복구하여 영속화한다")
    void shouldRecoverStaleProcessingKeyOnNextFlush() {
        String staleProcessingKey = keyManager.generateProcessingKey();
        Instant t1 = Instant.parse("2026-10-15T12:00:10Z");
        String staleRecord = String.join("\u001F", "stream-stale", "session-stale", "NORMAL", String.valueOf(t1.toEpochMilli()), "500", "0");
        redisTemplate.opsForList().rightPushAll(staleProcessingKey, List.of(staleRecord));

        Instant t2 = Instant.parse("2026-10-15T12:01:10Z");
        List<AnalysisSignal> newSignals = List.of(
            new AnalysisSignal("stream-new", "session-new", "PEAK", t2, 200L, 60_000L)
        );
        bufferService.bufferSignals(newSignals);

        int totalPersisted = bufferService.flushAndPersist();

        assertThat(totalPersisted).isEqualTo(2);
        List<AnalysisSignalSummaryDto> stored = summaryRepository.getStorage();
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(AnalysisSignalSummaryDto::streamId)
            .containsExactlyInAnyOrder("stream-stale", "stream-new");

        Set<String> remainingKeys = redisTemplate.keys(keyManager.getProcessingKeyPattern());
        assertThat(remainingKeys).isEmpty();
    }
}
