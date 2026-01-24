package io.slice.stream.engine.global.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public RedisScript<List> updateStreamScript() {
        ClassPathResource scriptSource = new ClassPathResource("lua/redis_stream_update.lua");
        return RedisScript.of(scriptSource, List.class);
    }

    @Bean
    public RedisScript<Long> tsAddScript() {
        ClassPathResource scriptSource = new ClassPathResource("lua/ts_add.lua");
        return RedisScript.of(scriptSource, Long.class);
    }

    @Bean
    public RedisScript<List> tsGetScript() {
        return RedisScript.of("return redis.call('TS.GET', KEYS[1])", List.class);
    }
}
