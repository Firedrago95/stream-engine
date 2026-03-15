package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.detection.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.detection.DetectionResult;
import io.slice.stream.engine.analyzer.domain.detection.HighlightDetector;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatFirepowerDetector implements HighlightDetector {

    private static final int MIN_DATA_POINTS_FOR_ANALYSIS = 10;
    private static final String MAX_FETCH_COUNT = "100";

    @Value("${highlight.range}")
    private Duration highlightRange;

    @Value("${highlight.z-score-threshold}")
    private double zScoreThreshold;

    @Value("${highlight.min-firepower-delta}")
    private long minFirepowerDelta;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tsRangeScript;
    private final Clock clock;

    @Override
    public DetectionResult detect(String chatRoomId) {
        List<List<Object>> cumulativeValues = fetchCumulativeValues(chatRoomId);

        if (cumulativeValues == null || cumulativeValues.size() < MIN_DATA_POINTS_FOR_ANALYSIS) {
            return DetectionResult.waiting();
        }

        List<Long> deltas = convertToDeltas(cumulativeValues, chatRoomId);
        if (deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS) return DetectionResult.waiting();

        return analyzeWithZScore(deltas, chatRoomId);
    }

    private DetectionResult analyzeWithZScore(List<Long> deltas, String chatRoomId) {
        Long currentDelta = deltas.get(deltas.size() - 1);

        if (currentDelta < minFirepowerDelta) {
            return new DetectionResult(ChatFirepowerStatus.NORMAL, currentDelta);
        }

        List<Long> history = deltas.subList(0, deltas.size() - 1);

        double mean = history.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double stdDev = calculateStandardDeviation(history, mean);

        if (stdDev < 0.0001) {
            ChatFirepowerStatus status = (currentDelta > mean) ? ChatFirepowerStatus.PEAK : ChatFirepowerStatus.NORMAL;
            return new DetectionResult(status, currentDelta);
        }

        double zScore = (currentDelta - mean) / stdDev;

        ChatFirepowerStatus status = (zScore > zScoreThreshold) ? ChatFirepowerStatus.PEAK : ChatFirepowerStatus.NORMAL;
        if (status == ChatFirepowerStatus.PEAK) {
            log.info("[PEAK 감지] Stream: {}, Z-Score: {}, 현재화력: {}, 평균: {}, 표준편차: {}", chatRoomId,
                String.format("%.2f", zScore), currentDelta, String.format("%.2f", mean), String.format("%.2f", stdDev));
        }

        return new DetectionResult(status, currentDelta);
    }

    private double calculateStandardDeviation(List<Long> data, double mean) {
        double variance = data.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0.0);
        return Math.sqrt(variance);
    }

    private List<List<Object>> fetchCumulativeValues(String chatRoomId) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, chatRoomId);
        long toTs = clock.instant().toEpochMilli();
        long fromTs = toTs - highlightRange.toMillis();

        @SuppressWarnings("unchecked")
        List<List<Object>> result = (List<List<Object>>) redisTemplate.execute(
            tsRangeScript,
            List.of(key),
            String.valueOf(fromTs),
            String.valueOf(toTs),
            MAX_FETCH_COUNT
        );

        if (log.isDebugEnabled()) {
            log.debug("[Redis-Fetch] 조회 결과 - Stream: {}, 수집갯수: {}, 데이터내용: {}",
                chatRoomId, (result == null ? 0 : result.size()), result);
        }
        return result;
    }

    private List<Long> convertToDeltas(List<List<Object>> cumulativeValues, String chatRoomId) {
        List<Long> deltas = new ArrayList<>();
        long previousValue = Long.parseLong((String) cumulativeValues.getFirst().get(1));

        for (int i = 1; i < cumulativeValues.size(); i++) {
            long currentValue = Long.parseLong((String) cumulativeValues.get(i).get(1));
            long delta = currentValue - previousValue;

            if (delta < 0) {
                log.warn("음수 변화량 감지 - chatRoomId: {}, 이전 누적: {}, 현재 누적: {}. 변화량을 0으로 처리합니다.",
                    chatRoomId, previousValue, currentValue);
                delta = 0;
            }
            deltas.add(delta);
            previousValue = currentValue;
        }
        return deltas;
    }
}

