package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.core.redis.Rediskeys;
import io.slice.stream.engine.analyzer.domain.similarity.StreamerChatterRepository;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStreamerChatterRepository implements StreamerChatterRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveChatters(String streamId, Set<Long> userHashes, String yearWeek) {
        if (userHashes == null || userHashes.isEmpty()) {
            return;
        }

        String key = String.format(Rediskeys.STREAMER_CHATTERS_PREFIX, streamId, yearWeek);
        String[] values = userHashes.stream()
            .map(String::valueOf)
            .toArray(String[]::new);

        try {
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForSet().add(key, (Object[]) values);
                    operations.expire(key, Duration.ofSeconds(Rediskeys.STREAMER_CHATTERS_TTL_SECONDS));
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("[Redis-Chatters] 스트리머 유저 해시 저장 실패 - Stream: {}, Key: {}, 건수: {}",
                streamId, key, userHashes.size(), e);
        }
    }

    @Override
    public Set<Long> findChatters(String streamId, String yearWeek) {
        String key = String.format(Rediskeys.STREAMER_CHATTERS_PREFIX, streamId, yearWeek);
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }

        return members.stream()
            .map(Long::parseLong)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<Long> findChatters(String streamId, List<String> yearWeeks) {
        if (yearWeeks == null || yearWeeks.isEmpty()) {
            return Collections.emptySet();
        }

        if (yearWeeks.size() == 1) {
            return findChatters(streamId, yearWeeks.getFirst());
        }

        List<String> keys = yearWeeks.stream()
            .map(yw -> String.format(Rediskeys.STREAMER_CHATTERS_PREFIX, streamId, yw))
            .toList();

        String firstKey = keys.getFirst();
        List<String> otherKeys = keys.subList(1, keys.size());

        Set<String> unionMembers = redisTemplate.opsForSet().union(firstKey, otherKeys);
        if (unionMembers == null || unionMembers.isEmpty()) {
            return Collections.emptySet();
        }

        return unionMembers.stream()
            .map(Long::parseLong)
            .collect(Collectors.toSet());
    }
}
