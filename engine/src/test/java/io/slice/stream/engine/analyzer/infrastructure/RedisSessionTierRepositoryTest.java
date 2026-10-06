package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.slice.stream.core.redis.Rediskeys;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisSessionTierRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisSessionTierRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisSessionTierRepository(redisTemplate);
    }

    @Test
    void save_정상적인_세션_ID와_바닥값을_TTL과_함께_저장한다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        repository.save(12345L, 45L);

        String expectedKey = String.format(Rediskeys.SESSION_TIER_PREFIX, 12345L);
        verify(valueOps).set(
            eq(expectedKey),
            eq("45"),
            eq(Duration.ofSeconds(Rediskeys.SESSION_TIER_TTL_SECONDS))
        );
    }

    @Test
    void save_세션_ID가_0_이하이면_저장하지_않는다() {
        repository.save(0L, 45L);
        repository.save(-1L, 45L);
    }

    @Test
    void findByLiveId_저장된_값이_존재하면_정상적으로_파싱하여_반환한다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        String expectedKey = String.format(Rediskeys.SESSION_TIER_PREFIX, 12345L);
        when(valueOps.get(expectedKey)).thenReturn("55");

        Optional<Long> result = repository.findByLiveId(12345L);

        assertThat(result).contains(55L);
    }

    @Test
    void findByLiveId_저장된_값이_없으면_빈_Optional을_반환한다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        String expectedKey = String.format(Rediskeys.SESSION_TIER_PREFIX, 99999L);
        when(valueOps.get(expectedKey)).thenReturn(null);

        Optional<Long> result = repository.findByLiveId(99999L);

        assertThat(result).isEmpty();
    }
}
