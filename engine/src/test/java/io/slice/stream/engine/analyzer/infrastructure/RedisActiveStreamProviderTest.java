package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.core.redis.Rediskeys;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisActiveStreamProviderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RedisActiveStreamProvider redisActiveStreamProvider;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void getActiveStreamIds_활성화된_스트림이_없을_때_빈_리스트를_반환한다() {
        // given
        when(setOperations.members(Rediskeys.ANALYSIS_INDEX)).thenReturn(Set.of());

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).isEmpty();
    }

    @Test
    void getActiveStreamIds_활성화된_스트림이_있을_때_올바른_스트림_ID_리스트를_반환한다() {
        // given
        Set<String> mockIds = Set.of("stream1", "stream2", "stream3");
        when(setOperations.members(Rediskeys.ANALYSIS_INDEX)).thenReturn(mockIds);

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).containsExactlyInAnyOrder("stream1", "stream2", "stream3");
    }

    @Test
    void getActiveStreamIds_redisTemplate_members가_null을_반환할_때_빈_리스트를_반환한다() {
        // given
        when(setOperations.members(Rediskeys.ANALYSIS_INDEX)).thenReturn(null);

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).isEmpty();
    }
}
