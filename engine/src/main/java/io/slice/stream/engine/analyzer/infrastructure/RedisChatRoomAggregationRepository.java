package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult.DataPoint;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisChatRoomAggregationRepository implements ChatRoomAggregationRepository {

    private static final String MAX_COUNT_FOR_FIND = "1000";
    private static final String MAX_COUNT_FOR_HISTORY = "2000";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tsAddScript;
    private final RedisScript<List> tsRangeScript;

    @Override
    public void save(ChatRoomAggregation chatRoomAggregation, Instant now) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, chatRoomAggregation.getStreamId());

        String count = String.valueOf(chatRoomAggregation.getCount());
        String timestamp = String.valueOf(now.toEpochMilli());
        String retention = String.valueOf(Rediskeys.CHAT_AGGREGATION_RETENTION);

        redisTemplate.execute(tsAddScript, List.of(key), timestamp, count, retention);
    }

    @Override
    public Optional<ChatAggregationResult> findByStreamId(String streamId) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, streamId);

        List<List<Object>> rawData = redisTemplate.execute(tsRangeScript, List.of(key), "-", "+", MAX_COUNT_FOR_FIND);

        if (rawData == null || rawData.isEmpty()) {
            return Optional.empty();
        }

        List<DataPoint> dataPoints = rawData.stream()
            .map(entry -> {
                long timestamp = ((Number) entry.get(0)).longValue();
                long value = Long.parseLong((String) entry.get(1));
                return new DataPoint(timestamp, value);
            })
            .toList();

        return Optional.of(new ChatAggregationResult(streamId, dataPoints));
    }

    @Override
    public List<Long> getFirepowerDeltas(String streamId, Instant from, Instant to) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, streamId);

        List<List<Object>> rawData = redisTemplate.execute(
            tsRangeScript,
            List.of(key),
            String.valueOf(from.toEpochMilli()),
            String.valueOf(to.toEpochMilli()),
            MAX_COUNT_FOR_HISTORY
        );

        if (rawData == null || rawData.size() < 2) {
            return List.of();
        }

        return calculateDeltas(streamId, rawData);
    }

    private static List<Long> calculateDeltas(String streamId, List<List<Object>> rawData) {
        List<Long> deltas = new ArrayList<>();
        long previousValue = Long.parseLong((String) rawData.getFirst().get(1));

        for (int i = 1; i < rawData.size(); i++) {
            long currentValue = Long.parseLong((String) rawData.get(i).get(1));
            long delta = currentValue - previousValue;

            if (delta < 0) {
                log.warn("[Redis] 음수 변화량 감지 - streamId: {}, 이전: {}, 현재: {}. 0 처리함.", streamId, previousValue, currentValue);
                continue;
            }
            deltas.add(delta);
            previousValue = currentValue;
        }
        return deltas;
    }
}
