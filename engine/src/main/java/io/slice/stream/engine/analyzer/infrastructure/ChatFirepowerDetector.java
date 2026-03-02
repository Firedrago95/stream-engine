package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.DetectionResult; // import 추가
import io.slice.stream.engine.analyzer.domain.HighlightDetector;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
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

    private static final int MIN_DATA_POINTS_FOR_ANALYSIS = 5;
    private static final String MAX_FETCH_COUNT = "100";

    @Value("${highlight.chat-firepower-multiplier}")
    private double chatFirepowerMultiplier;

    @Value("${highlight.range}")
    private Duration highlightRange;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tsRangeScript;
    private final Clock clock;

    @Override
    public DetectionResult detect(String chatRoomId) {
        List<List<Object>> cumulativeValues = fetchCumulativeValues(chatRoomId);

        if (cumulativeValues == null || cumulativeValues.size() < MIN_DATA_POINTS_FOR_ANALYSIS + 1) {
            if (log.isDebugEnabled()) {
                int size = (cumulativeValues == null) ? 0 : cumulativeValues.size();
                log.info("[Analysis-Step 1] 데이터 부족 (WAITING) - Stream: {}, 수집된 포인트: {}/6", chatRoomId, size);
            }
            return DetectionResult.waiting();
        }

        List<Long> deltas = convertToDeltas(cumulativeValues, chatRoomId);

        if (log.isDebugEnabled()) {
            log.debug("[Analysis-Step 2] 변화량 확인 - Stream: {}, Deltas: {}", chatRoomId, deltas);
        }

        return analyzeFirepower(deltas, chatRoomId);
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

    private DetectionResult analyzeFirepower(List<Long> deltas, String chatRoomId) {
        if (deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS) {
            return DetectionResult.waiting();
        }

        long lastValue = deltas.get(deltas.size() - 1);
        OptionalDouble average = deltas.stream()
            .limit(deltas.size() - 1)
            .mapToLong(v -> v)
            .average();

        if (average.isEmpty()) return DetectionResult.waiting();

        double avgValue = average.getAsDouble();
        double threshold = avgValue * chatFirepowerMultiplier;

        ChatFirepowerStatus status = (lastValue > threshold) ? ChatFirepowerStatus.PEAK : ChatFirepowerStatus.NORMAL;

        if (log.isDebugEnabled()) {
            log.info("[Analysis-Step 3] 판정 완료 - Stream: {}, 상태: {}, 현재: {}, 평균: {}, 임계치: {}",
                chatRoomId, status, lastValue, String.format("%.2f", avgValue), String.format("%.2f", threshold));
        }
        return new DetectionResult(status, lastValue);
    }
}

