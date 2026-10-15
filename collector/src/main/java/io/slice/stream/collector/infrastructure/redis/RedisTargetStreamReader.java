package io.slice.stream.collector.infrastructure.redis;

import io.slice.stream.collector.domain.TargetStreamReader;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.core.redis.Rediskeys;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTargetStreamReader implements TargetStreamReader {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    @Value("${targeting.live-hash-redis-key:stream:live:}")
    private String liveHashRedisKey = Rediskeys.STREAM_LIVE_HASH;

    @Override
    public List<StreamTarget> getTargetStreams() {
        try {
            List<Object> rawJsonList = redisTemplate.opsForHash().values(liveHashRedisKey);
            if (rawJsonList == null || rawJsonList.isEmpty()) {
                return Collections.emptyList();
            }

            return rawJsonList.stream()
                .filter(Objects::nonNull)
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .toList();
        } catch (Exception e) {
            log.error("[Redis 타겟 스트림 목록 조회 실패] 키: {}, 오류: {}", liveHashRedisKey, e.getMessage());
            throw new IllegalStateException("[Redis 타겟 스트림 목록 조회 실패] 키: " + liveHashRedisKey, e);
        }
    }

    private StreamTarget deserialize(Object rawJson) {
        try {
            return jsonMapper.readValue((String) rawJson, StreamTarget.class);
        } catch (Exception e) {
            log.warn("[StreamTarget 역직렬화 실패] 데이터: {}, 사유: {}", rawJson, e.getMessage());
            return null;
        }
    }
}
