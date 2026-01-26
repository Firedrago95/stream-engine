package io.slice.stream.testcontainer.redis;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class RedisTestContainer {

    private static final String REDIS_IMAGE = "redis/redis-stack-server:latest";
    private static final int REDIS_PORT = 6379;
    public static final GenericContainer<?> GENERIC_CONTAINER;

    static {
        GENERIC_CONTAINER = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT)
            .withReuse(true);

        GENERIC_CONTAINER.start();
    }
}
