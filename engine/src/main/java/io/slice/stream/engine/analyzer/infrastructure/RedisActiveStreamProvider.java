package io.slice.stream.engine.analyzer.infrastructure;

import io.slice.stream.engine.analyzer.domain.ActiveStreamProvider;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisActiveStreamProvider implements ActiveStreamProvider {

    private static final String KEY_PATTERN = "chat:analysis:*";
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getActiveStreamIds() {
        Set<String> keys = redisTemplate.keys(KEY_PATTERN);

        if (keys == null) return List.of();

        return keys.stream()
            .map(k -> k.replace("chat:analysis:", ""))
            .toList();
    }
}
