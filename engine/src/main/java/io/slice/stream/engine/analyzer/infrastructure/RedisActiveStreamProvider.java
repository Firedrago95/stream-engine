package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.ActiveStreamProvider;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisActiveStreamProvider implements ActiveStreamProvider {

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getActiveStreamIds() {
        Set<String> activeIds = redisTemplate.opsForSet().members(Rediskeys.ANALYSIS_INDEX);

        if (activeIds == null || activeIds.isEmpty()) return List.of();

        return new ArrayList<>(activeIds);
    }
}
