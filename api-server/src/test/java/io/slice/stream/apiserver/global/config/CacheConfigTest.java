package io.slice.stream.apiserver.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@DisplayNameGeneration(ReplaceUnderscores.class)
class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void activeSessions와_targetChannels_캐시가_독립적으로_등록된다() {
        CaffeineCacheManager cacheManager = cacheConfig.cacheManager();

        Cache activeSessionsCache = cacheManager.getCache("activeSessions");
        Cache targetChannelsCache = cacheManager.getCache("targetChannels");
        Cache similarityCache = cacheManager.getCache("streamerSimilarities");

        assertThat(activeSessionsCache).isNotNull();
        assertThat(targetChannelsCache).isNotNull();
        assertThat(similarityCache).isNotNull();

        targetChannelsCache.put("key", "test-value");
        assertThat(targetChannelsCache.get("key", String.class)).isEqualTo("test-value");

        similarityCache.put("streamer_1", "test-similarity");
        assertThat(similarityCache.get("streamer_1", String.class)).isEqualTo("test-similarity");
    }
}
