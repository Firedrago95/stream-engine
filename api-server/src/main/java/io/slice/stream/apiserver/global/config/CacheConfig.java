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
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("activeSessions");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)                              // 최대 1만 명의 스트리머 세션만 캐싱
            .expireAfterAccess(10, TimeUnit.MINUTES));  // 10분간 신호없으면 캐시 삭제
        return cacheManager;
    }
}
