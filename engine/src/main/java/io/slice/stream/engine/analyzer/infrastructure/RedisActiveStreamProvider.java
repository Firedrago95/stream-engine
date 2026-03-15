package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisActiveStreamProvider implements ActiveStreamProvider {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> getActiveTargetsScript;
    private final JsonMapper jsonMapper;

    @Override
    public List<String> getActiveStreamIds() {
        Set<String> activeIds = redisTemplate.opsForSet().members(Rediskeys.ANALYSIS_INDEX);

        if (activeIds == null || activeIds.isEmpty()) return List.of();

        return new ArrayList<>(activeIds);
    }

    @Override
    public List<StreamTarget> getActiveStreamTargets() {

        @SuppressWarnings("unchecked")
        List<String> rawTargets = redisTemplate.execute(
            getActiveTargetsScript,
            List.of(Rediskeys.ANALYSIS_INDEX, Rediskeys.STREAM_LIVE_HASH)
        );

        if (rawTargets == null || rawTargets.isEmpty()) {
            return List.of();
        }

        return rawTargets.stream()
            .filter(Objects::nonNull)
            .map(this::deserialize)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private StreamTarget deserialize(String json) {
        try {
            return jsonMapper.readValue(json, StreamTarget.class);
        } catch (Exception e) {
            log.error("[Redis-Load] StreamTarget 역직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
