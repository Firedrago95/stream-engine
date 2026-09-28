package io.slice.stream.apiserver.streamer.infrastructure;

import io.slice.stream.apiserver.streamer.domain.similarity.StreamerChatterProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamerChatterProvider implements StreamerChatterProvider {

    private static final String STREAMER_CHATTERS_PREFIX = "streamer:chatters:%s:%s";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Set<Long>> loadRecentChattersForStreamers(
        List<String> streamIds,
        List<String> yearWeeks
    ) {
        if (streamIds == null || streamIds.isEmpty() || yearWeeks == null || yearWeeks.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<Long>> result = new HashMap<>();
        for (String streamId : streamIds) {
            Set<Long> chatters = loadChattersForStream(streamId, yearWeeks);
            result.put(streamId, chatters);
        }

        log.info("[Redis-Chatters] 스트리머 유저 풀 로드 완료 - 대상 스트리머: {}명, 분석 주차: {}개",
            streamIds.size(), yearWeeks.size());

        return result;
    }

    private Set<Long> loadChattersForStream(String streamId, List<String> yearWeeks) {
        if (yearWeeks.size() == 1) {
            String key = String.format(STREAMER_CHATTERS_PREFIX, streamId, yearWeeks.getFirst());
            Set<String> members = redisTemplate.opsForSet().members(key);
            return convertToLongSet(members);
        }

        List<String> keys = yearWeeks.stream()
            .map(yw -> String.format(STREAMER_CHATTERS_PREFIX, streamId, yw))
            .toList();

        String firstKey = keys.getFirst();
        List<String> otherKeys = keys.subList(1, keys.size());

        Set<String> unionMembers = redisTemplate.opsForSet().union(firstKey, otherKeys);
        return convertToLongSet(unionMembers);
    }

    private Set<Long> convertToLongSet(Set<String> stringSet) {
        if (stringSet == null || stringSet.isEmpty()) {
            return Collections.emptySet();
        }
        return stringSet.stream()
            .map(Long::parseLong)
            .collect(Collectors.toSet());
    }
}
