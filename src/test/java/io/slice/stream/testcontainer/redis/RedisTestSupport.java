package io.slice.stream.testcontainer.redis;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
public interface RedisTestSupport {

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisTestContainer.GENERIC_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> RedisTestContainer.GENERIC_CONTAINER.getMappedPort(6379));
    }
}
