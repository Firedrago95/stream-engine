package io.slice.stream.engine.highlight.infrastructure;

import io.slice.stream.engine.highlight.domain.ChatFirepowerStatus;
import io.slice.stream.engine.highlight.domain.HighlightDetector;
import java.time.Clock;
import java.time.Duration;
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

    private static final String CHAT_ANALYSIS_KEY = "chat:analysis:%s";
    private static final int MIN_DATA_POINTS = 5;

    @Value("${highlight.chat-firepower-multiplier}")
    private double chatFirepowerMultiplier;

    @Value("${highlight.range}")
    private Duration highlightRange;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tsRangeScript;
    private final Clock clock;

    @Override
    public ChatFirepowerStatus detect(String chatRoomId) {
        String key = String.format(CHAT_ANALYSIS_KEY, chatRoomId);
        long toTs = clock.instant().toEpochMilli();
        long fromTs = toTs - highlightRange.toMillis();

        List<List<Object>> values = redisTemplate.execute(tsRangeScript, List.of(key), String.valueOf(fromTs), String.valueOf(toTs));

        if (values == null || values.size() < MIN_DATA_POINTS) {
            return ChatFirepowerStatus.WAITING;
        }

        long lastValue = Long.parseLong((String) values.getLast().get(1));

        OptionalDouble average = values.stream()
            .limit(values.size() - 1)
            .mapToLong(v -> Long.parseLong((String) v.get(1)))
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

