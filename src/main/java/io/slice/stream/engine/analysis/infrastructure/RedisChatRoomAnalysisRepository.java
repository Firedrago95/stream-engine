package io.slice.stream.engine.analysis.infrastructure;

import io.slice.stream.engine.analysis.domain.ChatCountHistory;
import io.slice.stream.engine.analysis.domain.ChatCountHistory.DataPoint;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysisRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisChatRoomAnalysisRepository implements ChatRoomAnalysisRepository {

    private static final String CHAT_ANALYSIS_KEY = "chat:analysis:%s";
    private static final long RETENTION = 604_800_000; // 7일

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> tsAddScript;
    private final RedisScript<List> tsRangeScript;

    @Override
    public void save(ChatRoomAnalysis chatRoomAnalysis, Instant now) {
        String key = String.format(CHAT_ANALYSIS_KEY, chatRoomAnalysis.getStreamId());

        String count = String.valueOf(chatRoomAnalysis.getCount());
        String timestamp = String.valueOf(now.toEpochMilli());
        String retention = String.valueOf(RETENTION);

        redisTemplate.execute(tsAddScript, List.of(key), timestamp, count, retention);
    }

    @Override
    public Optional<ChatCountHistory> findByStreamId(String streamId) {
        String key = String.format(CHAT_ANALYSIS_KEY, streamId);
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

        return Optional.of(new ChatCountHistory(streamId, dataPoints));
    }
}
