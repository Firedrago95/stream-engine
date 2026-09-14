package io.slice.stream.engine.ingestion.domain.targeting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TargetStreamPool {

    private final TargetStreamProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final Set<String> customChannels = ConcurrentHashMap.newKeySet();

    public boolean isTarget(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return false;
        }

        if (customChannels.contains(channelId)) {
            return true;
        }

        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(properties.getTrendRedisKey(), channelId);
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("[Targeting] Redis 타겟 풀 조회 중 오류 발생. channelId: {}", channelId, e);
            return false;
        }
    }

    public void addCustomTarget(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }

        customChannels.add(channelId);
        try {
            redisTemplate.opsForSet().add(properties.getTrendRedisKey(), channelId);
        } catch (Exception e) {
            log.warn("[Targeting] 커스텀 타겟 Redis 등록 중 오류 발생. channelId: {}", channelId, e);
        }
        log.info("[Targeting] 커스텀 타겟 채널이 추가되었습니다. channelId: {}", channelId);
    }

    private static final RedisScript<Long> SYNC_TARGETS_SCRIPT = RedisScript.of(
        "redis.call('DEL', KEYS[1])\n" +
        "if #ARGV > 0 then\n" +
        "    redis.call('SADD', KEYS[1], unpack(ARGV))\n" +
        "end\n" +
        "return 1\n",
        Long.class
    );

    public void syncTargets(Set<String> newTargets) {
        if (newTargets == null) {
            return;
        }

        try {
            String key = properties.getTrendRedisKey();
            Set<String> allTargets = new HashSet<>(newTargets);
            allTargets.addAll(customChannels);

            if (allTargets.isEmpty()) {
                redisTemplate.delete(key);
            } else {
                redisTemplate.execute(
                    SYNC_TARGETS_SCRIPT,
                    List.of(key),
                    (Object[]) allTargets.toArray(new String[0])
                );
            }
            log.info("[Targeting] 타겟 스트리머 명단이 동기화되었습니다. (총 {}개 채널)", allTargets.size());
        } catch (Exception e) {
            log.error("[Targeting] 타겟 스트리머 명단 동기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    public Set<String> getAllActiveTargetChannels() {
        Set<String> allTargets = new HashSet<>(customChannels);
        try {
            Set<String> redisMembers = redisTemplate.opsForSet().members(properties.getTrendRedisKey());
            if (redisMembers != null) {
                allTargets.addAll(redisMembers);
            }
        } catch (Exception e) {
            log.warn("[Targeting] Redis 타겟 풀 전체 조회 중 오류 발생: {}", e.getMessage(), e);
        }
        return allTargets;
    }
}
