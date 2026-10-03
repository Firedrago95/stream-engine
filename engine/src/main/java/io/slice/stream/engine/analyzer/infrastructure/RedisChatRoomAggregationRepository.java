package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.core.redis.Rediskeys;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult.DataPoint;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatSummary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class RedisChatRoomAggregationRepository implements ChatRoomAggregationRepository {

    private static final String MAX_COUNT_FOR_FIND = "1000";
    private static final String MAX_COUNT_FOR_HISTORY = "2000";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tsAddScript;
    private final RedisScript<List> tsRangeScript;
    private final long tickIntervalMs;

    public RedisChatRoomAggregationRepository(
        StringRedisTemplate redisTemplate,
        RedisScript<Long> tsAddScript,
        RedisScript<List> tsRangeScript,
        @Value("${highlight.engine.aggregation-interval-ms:3000}") long tickIntervalMs
    ) {
        this.redisTemplate = redisTemplate;
        this.tsAddScript = tsAddScript;
        this.tsRangeScript = tsRangeScript;
        this.tickIntervalMs = tickIntervalMs;
    }

    @Override
    public void save(ChatRoomAggregation chatRoomAggregation, Instant now) {
        save(chatRoomAggregation.getStreamId(), chatRoomAggregation.getCount(), now);
    }

    @Override
    public void save(String streamId, long totalCount, Instant now) {
        String key = String.format(Rediskeys.CHAT_AGGREGATION_PREFIX, streamId);

        String count = String.valueOf(totalCount);
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

    private List<Long> calculateDeltas(String streamId, List<List<Object>> rawData) {
        List<Long> deltas = new ArrayList<>();

        long previousTimestamp = ((Number) rawData.getFirst().get(0)).longValue();
        long previousValue = Long.parseLong((String) rawData.getFirst().get(1));

        for (int i = 1; i < rawData.size(); i++) {
            long currentTimestamp = ((Number) rawData.get(i).get(0)).longValue();
            long currentValue = Long.parseLong((String) rawData.get(i).get(1));

            long timeGap = currentTimestamp - previousTimestamp;
            if (timeGap > tickIntervalMs) {
                int missingTicks = (int) (timeGap / tickIntervalMs) - 1;
                for (int j = 0; j < missingTicks; j++) {
                    deltas.add(0L);
                }
            }

            long delta = currentValue - previousValue;
            if (delta < 0) {
                log.warn("[Redis] 카운터 리셋 감지 - streamId: {}, 이전: {}, 현재: {}. 스킵합니다.", streamId, previousValue, currentValue);
                previousValue = currentValue;
                previousTimestamp = currentTimestamp;
                continue;
            }

            deltas.add(delta);
            previousValue = currentValue;
            previousTimestamp = currentTimestamp;
        }
        return deltas;
    }

    @Override
    public ChatSummary incrementSummary(String streamId, long deltaTotal, long deltaSubscriber) {
        String key = String.format(Rediskeys.CHAT_SUMMARY_PREFIX, streamId);

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
            byte[] rawFieldTotal = Rediskeys.CHAT_SUMMARY_FIELD_TOTAL.getBytes(StandardCharsets.UTF_8);
            byte[] rawFieldSub = Rediskeys.CHAT_SUMMARY_FIELD_SUBSCRIBER.getBytes(StandardCharsets.UTF_8);

            connection.hashCommands().hIncrBy(rawKey, rawFieldTotal, deltaTotal);
            connection.hashCommands().hIncrBy(rawKey, rawFieldSub, deltaSubscriber);
            connection.keyCommands().expire(rawKey, Rediskeys.CHAT_SUMMARY_TTL_SECONDS);
            return null;
        });

        long total = parseLongFromPipeline(results, 0);
        long subscriber = parseLongFromPipeline(results, 1);

        return new ChatSummary(total, subscriber);
    }

    @Override
    public Optional<ChatSummary> findSummaryByStreamId(String streamId) {
        String key = String.format(Rediskeys.CHAT_SUMMARY_PREFIX, streamId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        long total = parseLongFromEntry(entries.get(Rediskeys.CHAT_SUMMARY_FIELD_TOTAL));
        long subscriber = parseLongFromEntry(entries.get(Rediskeys.CHAT_SUMMARY_FIELD_SUBSCRIBER));

        return Optional.of(new ChatSummary(total, subscriber));
    }

    @Override
    public void deleteSummary(String streamId) {
        String key = String.format(Rediskeys.CHAT_SUMMARY_PREFIX, streamId);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("[Redis] 스트림 요약 키 삭제 완료 - Key: {}", key);
        }
    }

    private long parseLongFromPipeline(List<Object> results, int index) {
        if (results == null || results.size() <= index || results.get(index) == null) {
            return 0L;
        }
        Object value = results.get(index);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private long parseLongFromEntry(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
