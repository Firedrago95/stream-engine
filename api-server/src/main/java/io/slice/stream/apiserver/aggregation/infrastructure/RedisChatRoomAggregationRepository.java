package io.slice.stream.apiserver.aggregation.infrastructure;

import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult;
import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult.DataPoint;
import io.slice.stream.apiserver.aggregation.domain.ChatRoomAggregation;
import io.slice.stream.apiserver.aggregation.domain.ChatRoomAggregationRepository;
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

    private static final String CHAT_AGGREGATION_KEY = "chat:aggregation:%s";
    private static final long RETENTION = 604_800_000; // 7일

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tsAddScript;
    private final RedisScript<List> tsRangeScript;

    @Override
    public void save(ChatRoomAggregation chatRoomAggregation, Instant now) {
        String key = String.format(CHAT_AGGREGATION_KEY, chatRoomAggregation.getStreamId());

        String count = String.valueOf(chatRoomAggregation.getCount());
        String timestamp = String.valueOf(now.toEpochMilli());
        String retention = String.valueOf(RETENTION);

        redisTemplate.execute(tsAddScript, List.of(key), timestamp, count, retention);
    }

    @Override
    public Optional<ChatAggregationResult> findByStreamId(String streamId) {
        String key = String.format(CHAT_AGGREGATION_KEY, streamId);
        List<List<Object>> rawData = redisTemplate.execute(tsRangeScript, List.of(key), "-", "+");

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
