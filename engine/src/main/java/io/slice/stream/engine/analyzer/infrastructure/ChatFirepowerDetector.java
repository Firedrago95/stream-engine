package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.HighlightDetector;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatFirepowerDetector implements HighlightDetector {

    private static final String CHAT_AGGREGATION_KEY = "chat:aggregation:%s";
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
    public ChatFirepowerStatus detect(String chatRoomId) {
        List<List<Object>> cumulativeValues = fetchCumulativeValues(chatRoomId);

        if (cumulativeValues == null || cumulativeValues.size() < MIN_DATA_POINTS_FOR_ANALYSIS + 1) {
            return ChatFirepowerStatus.WAITING;
        }

        List<Long> deltas = convertToDeltas(cumulativeValues);

        return analyzeFirepower(deltas);
    }

    private List<List<Object>> fetchCumulativeValues(String chatRoomId) {
        String key = String.format(CHAT_AGGREGATION_KEY, chatRoomId);
        long toTs = clock.instant().toEpochMilli();
        long fromTs = toTs - highlightRange.toMillis();
        return redisTemplate.execute(tsRangeScript, List.of(key), String.valueOf(fromTs), String.valueOf(toTs), MAX_FETCH_COUNT);
    }

    private List<Long> convertToDeltas(List<List<Object>> cumulativeValues) {
        List<Long> deltas = new ArrayList<>();
        long previousValue = Long.parseLong((String) cumulativeValues.getFirst().get(1));

        for (int i = 1; i < cumulativeValues.size(); i++) {
            long currentValue = Long.parseLong((String) cumulativeValues.get(i).get(1));
            long delta = currentValue - previousValue;

            if (delta >= 0) {
                deltas.add(delta);
            }
            previousValue = currentValue;
        }
        return deltas;
    }

    private ChatFirepowerStatus analyzeFirepower(List<Long> deltas) {
        if (deltas.size() < MIN_DATA_POINTS_FOR_ANALYSIS) {
            return ChatFirepowerStatus.WAITING;
        }

        long lastValue = deltas.getLast();
        OptionalDouble average = deltas.stream()
            .limit(deltas.size() - 1)
            .mapToLong(v -> v)
            .average();

        if (average.isEmpty()) {
            return ChatFirepowerStatus.WAITING;
        }

        if (lastValue > average.getAsDouble() * chatFirepowerMultiplier) {
            return ChatFirepowerStatus.PEAK;
        }

        return ChatFirepowerStatus.NORMAL;
    }
}

