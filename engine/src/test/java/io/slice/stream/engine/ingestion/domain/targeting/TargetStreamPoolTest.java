package io.slice.stream.engine.ingestion.domain.targeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetStreamPoolTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private TargetStreamProperties properties;
    private TargetStreamPool targetStreamPool;
    private final String trendRedisKey = "target:streamers:pool";

    @BeforeEach
    void setUp() {
        properties = new TargetStreamProperties();
        properties.setTrendRedisKey(trendRedisKey);
        targetStreamPool = new TargetStreamPool(properties, redisTemplate);
    }

    @Test
    void Redis_Set에_존재하는_채널이면_isTarget이_true를_반환한다() {
        String channelId = "ch_trend";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(trendRedisKey, channelId)).thenReturn(true);

        boolean result = targetStreamPool.isTarget(channelId);

        assertThat(result).isTrue();
    }

    @Test
    void Redis_Set에_존재하지_않는_채널이면_isTarget이_false를_반환한다() {
        String channelId = "ch_normal";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(trendRedisKey, channelId)).thenReturn(false);

        boolean result = targetStreamPool.isTarget(channelId);

        assertThat(result).isFalse();
    }

    @Test
    void addCustomTarget으로_추가된_채널은_isTarget이_true를_반환한다() {
        String channelId = "ch_custom";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        targetStreamPool.addCustomTarget(channelId);

        verify(setOperations).add(trendRedisKey, channelId);
        assertThat(targetStreamPool.isTarget(channelId)).isTrue();
    }

    @Test
    void syncTargets_호출_시_기존_키를_삭제하고_새_타겟들을_Redis에_등록한다() {
        Set<String> newTargets = Set.of("ch1", "ch2");
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        targetStreamPool.syncTargets(newTargets);

        verify(redisTemplate).delete(trendRedisKey);
        verify(setOperations).add(eq(trendRedisKey), any(String[].class));
    }

    @Test
    void getAllActiveTargetChannels는_Redis_멤버와_커스텀_타겟을_합친_Set을_반환한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(trendRedisKey)).thenReturn(Set.of("ch1", "ch2"));

        targetStreamPool.addCustomTarget("ch_custom");

        Set<String> allActive = targetStreamPool.getAllActiveTargetChannels();

        assertThat(allActive).containsExactlyInAnyOrder("ch1", "ch2", "ch_custom");
    }

    @Test
    void channelId가_null이거나_공백이면_false를_반환한다() {
        assertThat(targetStreamPool.isTarget(null)).isFalse();
        assertThat(targetStreamPool.isTarget("")).isFalse();
        assertThat(targetStreamPool.isTarget("   ")).isFalse();
    }

    @Test
    void Redis_조회_중_예외가_발생하면_false를_반환한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(trendRedisKey, "ch_error")).thenThrow(new RuntimeException("Redis 연결 에러"));

        boolean result = targetStreamPool.isTarget("ch_error");

        assertThat(result).isFalse();
    }
}
