package io.slice.stream.engine.ingestion.infrastructure.redis;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.StreamRepository;
import io.slice.stream.engine.ingestion.domain.StreamUpdateResults;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStreamRepository implements StreamRepository {

    private static final String TEMP_KEY = "streams.active.temp";
    private static final String ACTUAL_KEY = "streams.active.id";
    private static final String INFO_KEY = "streams.info";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public StreamUpdateResults update(List<StreamTarget> streamTargets) {
        stringRedisTemplate.delete(TEMP_KEY);

        String[] addedIds = streamTargets.stream()
            .map(StreamTarget::channelId)
            .toArray(String[]::new);

        if (streamTargets.isEmpty()) {
            log.warn("Ingested streams are empty. Check if the external API is working correctly.");
            return new StreamUpdateResults(Set.of(), Set.of());
        }

        stringRedisTemplate.opsForSet().add(TEMP_KEY, addedIds);
        Set<String> newStreamIds = stringRedisTemplate.opsForSet().difference(TEMP_KEY, ACTUAL_KEY);
        Set<String> closedStreamIds = stringRedisTemplate.opsForSet().difference(ACTUAL_KEY, TEMP_KEY);

        redisTemplate.rename(TEMP_KEY, ACTUAL_KEY);

        updateStreamDetails(streamTargets, closedStreamIds);

        return new StreamUpdateResults(newStreamIds, closedStreamIds);
    }

    private void updateStreamDetails(List<StreamTarget> streamTargets, Set<String> closedStreamIds) {
        Map<String, StreamTarget> streamMap = streamTargets.stream()
            .collect(Collectors.toMap(
                StreamTarget::channelId,
                streamTarget -> streamTarget
            ));

        if (!streamMap.isEmpty()) {
            redisTemplate.opsForHash().putAll(INFO_KEY, streamMap);
        }

        if (closedStreamIds != null && !closedStreamIds.isEmpty()) {
            redisTemplate.opsForHash().delete(INFO_KEY, closedStreamIds.toArray());
        }
    }
}
