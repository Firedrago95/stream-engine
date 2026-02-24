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
            return DetectionResult.waiting();
        }

        List<Long> deltas = convertToDeltas(cumulativeValues, chatRoomId);

        return analyzeFirepower(deltas);
    }

    private List<List<Object>> fetchCumulativeValues(String chatRoomId) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, chatRoomId);
        long toTs = clock.instant().toEpochMilli();
        long fromTs = toTs - highlightRange.toMillis();
        return redisTemplate.execute(tsRangeScript, List.of(key), String.valueOf(fromTs), String.valueOf(toTs), MAX_FETCH_COUNT);
    }

    private List<Long> convertToDeltas(List<List<Object>> cumulativeValues, String chatRoomId) {
        List<Long> deltas = new ArrayList<>();
        long previousValue = Long.parseLong((String) cumulativeValues.getFirst().get(1));

        for (int i = 1; i < cumulativeValues.size(); i++) {
            long currentValue = Long.parseLong((String) cumulativeValues.get(i).get(1));
            long delta = currentValue - previousValue;

            if (delta < 0) {
                log.warn("음수 변화량 감지(카운터 리셋 의심) - chatRoomId: {}, 이전 누적: {}, 현재 누적: {}. 변화량을 0으로 처리합니다.",
                    chatRoomId, previousValue, currentValue);
                delta = 0;
            }
            deltas.add(delta);
            previousValue = currentValue;
        }
        return deltas;
    }

    private DetectionResult analyzeFirepower(List<Long> deltas) {
        if (deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS) {
            return DetectionResult.waiting();
        }

        long lastValue = deltas.getLast();
        OptionalDouble average = deltas.stream()
            .limit(deltas.size() - 1)
            .mapToLong(v -> v)
            .average();

        if (average.isEmpty()) {
            return DetectionResult.waiting();
        }

        ChatFirepowerStatus status;
        if (lastValue > average.getAsDouble() * chatFirepowerMultiplier) {
            status = ChatFirepowerStatus.PEAK;
        } else {
            status = ChatFirepowerStatus.NORMAL;
        }
        return new DetectionResult(status, lastValue);
    }
}

