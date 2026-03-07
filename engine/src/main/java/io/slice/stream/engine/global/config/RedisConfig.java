package io.slice.stream.engine.global.config;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public ClientResources clientResources(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();

        if (meterRegistry == null) {
            return DefaultClientResources.create();
        }

        return DefaultClientResources.builder()
            .commandLatencyRecorder(new MicrometerCommandLatencyRecorder(
                meterRegistry,
                MicrometerOptions.create()
            ))
            .build();
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .clientResources(clientResources)
            .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);

        return new LettuceConnectionFactory(serverConfig, clientConfig);
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

    @Bean
    public RedisScript<List> tsRangeScript() {
        ClassPathResource scriptSource = new ClassPathResource("lua/ts_range.lua");
        return RedisScript.of(scriptSource, List.class);
    }

    @Bean
    public RedisScript<List> getActiveTargetsScript() {
        ClassPathResource scriptSource = new ClassPathResource("lua/get_active_targets.lua");
        return RedisScript.of(scriptSource, List.class);
    }
}
