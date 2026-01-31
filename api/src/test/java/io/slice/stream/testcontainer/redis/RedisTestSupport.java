package io.slice.stream.testcontainer.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public interface RedisTestSupport {

    DockerImageName REDIS_STACK_IMAGE =
        DockerImageName.parse("redis/redis-stack-server:7.2.0-v9");

    @Container
    GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(REDIS_STACK_IMAGE)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add(
            "spring.data.redis.port",
            () -> REDIS_CONTAINER.getMappedPort(6379)
        );
    }
}
