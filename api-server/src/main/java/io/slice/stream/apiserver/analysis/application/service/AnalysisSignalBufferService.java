package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.AnalysisRedisKeyManager;
import io.slice.stream.apiserver.analysis.infrastructure.AnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisSignalBufferService {

    private static final String DELIMITER = "\u001F";
    private static final long ONE_MINUTE_MS = 60_000L;
    private static final Duration PROCESSING_KEY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisSignalSummaryJdbcRepository summaryRepository;
    private final AnalysisRedisKeyManager keyManager;

    public void bufferSignals(List<AnalysisSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }

        List<String> serialized = serializeSignals(signals);
        stringRedisTemplate.opsForList().rightPushAll(keyManager.getBufferKey(), serialized);
        log.debug("[Signal-Buffer] 분석 신호 버퍼 적재 완료 - 건수: {}", signals.size());
    }

    public int flushAndPersist() {
        int recoveredCount = processStaleProcessingKeys();

        String bufferKey = keyManager.getBufferKey();
        Long size = stringRedisTemplate.opsForList().size(bufferKey);
        if (size == null || size == 0L) {
            return recoveredCount;
        }

        String processingKey = keyManager.generateProcessingKey();
        try {
            stringRedisTemplate.rename(bufferKey, processingKey);
            stringRedisTemplate.expire(processingKey, PROCESSING_KEY_TTL);
        } catch (Exception e) {
            log.warn("[Signal-Buffer] 버퍼 키 롤오버 실패: {}", e.getMessage());
            return recoveredCount;
        }

        int processedCount = processAndPersistKey(processingKey);
        return recoveredCount + processedCount;
    }

    private int processStaleProcessingKeys() {
        Set<String> staleKeys = stringRedisTemplate.keys(keyManager.getProcessingKeyPattern());
        if (staleKeys == null || staleKeys.isEmpty()) {
            return 0;
        }

        int totalRecovered = 0;
        for (String staleKey : staleKeys) {
            try {
                log.info("[Signal-Buffer] 미처리된 잔여 processing 키 발견, 복구 처리 시작: {}", staleKey);
                totalRecovered += processAndPersistKey(staleKey);
            } catch (Exception e) {
                log.error("[Signal-Buffer] 잔여 키 복구 처리 실패 - 키: {}", staleKey, e);
            }
        }
        return totalRecovered;
    }

    private int processAndPersistKey(String processingKey) {
        List<String> rawRecords = stringRedisTemplate.opsForList().range(processingKey, 0, -1);
        if (rawRecords == null || rawRecords.isEmpty()) {
            stringRedisTemplate.delete(processingKey);
            return 0;
        }

        List<AnalysisSignalSummaryDto> summaries = aggregateSignals(rawRecords);
        if (!summaries.isEmpty()) {
            summaryRepository.batchUpsertSummaries(summaries);
            log.info("[Signal-Buffer] 분석 신호 사전 집계 영속화 완료 - 집계 건수: {} (원문: {}건)", summaries.size(), rawRecords.size());
        }

        stringRedisTemplate.delete(processingKey);
        return summaries.size();
    }

    private List<String> serializeSignals(List<AnalysisSignal> signals) {
        List<String> list = new ArrayList<>(signals.size());
        for (AnalysisSignal s : signals) {
            String record = String.join(
                DELIMITER,
                s.streamId() != null ? s.streamId() : "",
                s.sessionId() != null ? s.sessionId() : "",
                s.status() != null ? s.status() : "",
                String.valueOf(s.timestamp() != null ? s.timestamp().toEpochMilli() : 0L),
                String.valueOf(s.firepower()),
                String.valueOf(s.offsetMs() != null ? s.offsetMs() : 0L)
            );
            list.add(record);
        }
        return list;
    }

    private List<AnalysisSignalSummaryDto> aggregateSignals(List<String> rawRecords) {
        Map<SummaryGroupKey, SignalAggregator> grouped = new HashMap<>();

        for (String record : rawRecords) {
            parseAndAggregate(record, grouped);
        }

        List<AnalysisSignalSummaryDto> result = new ArrayList<>(grouped.size());
        for (Map.Entry<SummaryGroupKey, SignalAggregator> entry : grouped.entrySet()) {
            SummaryGroupKey key = entry.getKey();
            SignalAggregator agg = entry.getValue();
            long avg = agg.count > 0 ? agg.sumFirepower / agg.count : 0L;

            result.add(new AnalysisSignalSummaryDto(
                key.streamId(),
                key.sessionId(),
                key.status(),
                avg,
                agg.maxFirepower,
                Instant.ofEpochMilli(agg.minTimestampEpochMilli),
                key.minuteOffsetMs()
            ));
        }

        return result;
    }

    private void parseAndAggregate(String record, Map<SummaryGroupKey, SignalAggregator> grouped) {
        String[] tokens = record.split(DELIMITER, -1);
        if (tokens.length < 6) {
            return;
        }

        String streamId = tokens[0];
        String sessionId = tokens[1];
        String status = tokens[2];
        long timestampMilli = parseLongSafely(tokens[3]);
        long firepower = parseLongSafely(tokens[4]);
        long offsetMs = parseLongSafely(tokens[5]);

        long minuteOffsetMs = (offsetMs / ONE_MINUTE_MS) * ONE_MINUTE_MS;
        SummaryGroupKey groupKey = new SummaryGroupKey(streamId, sessionId, status, minuteOffsetMs);

        SignalAggregator aggregator = grouped.computeIfAbsent(groupKey, k -> new SignalAggregator());
        aggregator.add(firepower, timestampMilli);
    }

    private long parseLongSafely(String val) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private record SummaryGroupKey(String streamId, String sessionId, String status, long minuteOffsetMs) {}

    private static class SignalAggregator {
        private long sumFirepower = 0L;
        private long maxFirepower = Long.MIN_VALUE;
        private long minTimestampEpochMilli = Long.MAX_VALUE;
        private int count = 0;

        void add(long firepower, long timestampMilli) {
            sumFirepower += firepower;
            if (firepower > maxFirepower) {
                maxFirepower = firepower;
            }
            if (timestampMilli < minTimestampEpochMilli) {
                minTimestampEpochMilli = timestampMilli;
            }
            count++;
        }
    }
}
