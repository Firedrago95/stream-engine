package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.core.redis.Rediskeys;
import io.slice.stream.engine.analyzer.domain.tier.SessionTierRepository;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisSessionTierRepository implements SessionTierRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(long liveId, long noiseFloor) {
        if (liveId <= 0) {
            return;
        }

        try {
            String key = formatKey(liveId);
            redisTemplate.opsForValue().set(
                key,
                String.valueOf(noiseFloor),
                Duration.ofSeconds(Rediskeys.SESSION_TIER_TTL_SECONDS)
            );
        } catch (Exception e) {
            log.error("[SessionTierRepository] 세션 {} 바닥값({}) Redis 저장 실패", liveId, noiseFloor, e);
        }
    }

    @Override
    public Optional<Long> findByLiveId(long liveId) {
        if (liveId <= 0) {
            return Optional.empty();
        }

        try {
            String key = formatKey(liveId);
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            log.error("[SessionTierRepository] 세션 {} 바닥값 Redis 조회 실패", liveId, e);
            return Optional.empty();
        }
    }

    private String formatKey(long liveId) {
        return String.format(Rediskeys.SESSION_TIER_PREFIX, liveId);
    }
}
