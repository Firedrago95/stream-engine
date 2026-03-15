package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult.DataPoint;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisChatRoomAggregationRepository implements ChatRoomAggregationRepository {

    private static final String MAX_COUNT_FOR_FIND = "1000";

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
}
