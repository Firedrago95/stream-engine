package io.slice.stream.apiserver.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableCaching
@Configuration
public class CacheConfig {

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        cacheManager.registerCustomCache("activeSessions",
            Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build());

        cacheManager.registerCustomCache("targetChannels",
            Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(25, TimeUnit.HOURS)
                .build());

        return cacheManager;
    }
}
