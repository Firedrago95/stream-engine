package io.slice.stream.collector.infrastructure.redis;

import io.slice.stream.collector.domain.TargetStreamReader;
import io.slice.stream.core.redis.Rediskeys;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTargetStreamReader implements TargetStreamReader {

    private final StringRedisTemplate redisTemplate;

    @Value("${targeting.trend-redis-key:stream:targets}")
    private String trendRedisKey = Rediskeys.STREAM_TARGETS;

    @Override
    public Set<String> getTargetChannels() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(trendRedisKey);
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }
            return members;
        } catch (Exception e) {
            log.error("[Redis 타겟 풀 조회 실패] 키: {}, 오류: {}", trendRedisKey, e.getMessage());
            throw new IllegalStateException("[Redis 타겟 풀 조회 실패] 키: " + trendRedisKey, e);
        }
    }
}
