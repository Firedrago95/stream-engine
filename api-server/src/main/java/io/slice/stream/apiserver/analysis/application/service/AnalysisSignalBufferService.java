package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.AnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisSignalBufferService {

    private static final String BUFFER_KEY = "analysis:signals:buffer";
    private static final String PROCESSING_KEY = "analysis:signals:processing";
    private static final String DELIMITER = "\u001F";
    private static final long ONE_MINUTE_MS = 60_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalysisSignalSummaryJdbcRepository summaryRepository;

    public void bufferSignals(List<AnalysisSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }

        List<String> serialized = serializeSignals(signals);
        stringRedisTemplate.opsForList().rightPushAll(BUFFER_KEY, serialized);
        log.debug("[Signal-Buffer] 분석 신호 버퍼 적재 완료 - 건수: {}", signals.size());
    }

    public int flushAndPersist() {
        List<String> rawRecords = drainBuffer();
        if (rawRecords.isEmpty()) {
            return 0;
        }

        List<AnalysisSignalSummaryDto> summaries = aggregateSignals(rawRecords);
        if (summaries.isEmpty()) {
            return 0;
        }

        summaryRepository.batchUpsertSummaries(summaries);
        log.info("[Signal-Buffer] 분석 신호 사전 집계 영속화 완료 - 집계 건수: {} (원문: {}건)", summaries.size(), rawRecords.size());
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

    private List<String> drainBuffer() {
        try {
            Long size = stringRedisTemplate.opsForList().size(BUFFER_KEY);
            if (size == null || size == 0L) {
                return Collections.emptyList();
            }

            stringRedisTemplate.rename(BUFFER_KEY, PROCESSING_KEY);
            List<String> records = stringRedisTemplate.opsForList().range(PROCESSING_KEY, 0, -1);
            stringRedisTemplate.delete(PROCESSING_KEY);
            return records != null ? records : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[Signal-Buffer] 버퍼 드레인 중 예외 발생: {}", e.getMessage());
            return Collections.emptyList();
        }
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
