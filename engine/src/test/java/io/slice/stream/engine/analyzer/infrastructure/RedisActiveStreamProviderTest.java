package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisActiveStreamProviderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisActiveStreamProvider redisActiveStreamProvider;

    private static final String KEY_PATTERN = "chat:analysis:*";

    @Test
    void getActiveStreamIds_활성화된_스트림이_없을_때_빈_리스트를_반환한다() {
        // given
        when(redisTemplate.keys(KEY_PATTERN)).thenReturn(Set.of());

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).isEmpty();
    }

    @Test
    void getActiveStreamIds_활성화된_스트림이_있을_때_올바른_스트림_ID_리스트를_반환한다() {
        // given
        Set<String> mockKeys = Set.of("chat:analysis:stream1", "chat:analysis:stream2", "chat:analysis:stream3");
        when(redisTemplate.keys(KEY_PATTERN)).thenReturn(mockKeys);

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).containsExactlyInAnyOrder("stream1", "stream2", "stream3");
    }

    @Test
    void getActiveStreamIds_redisTemplate_keys가_null을_반환할_때_빈_리스트를_반환한다() {
        // given
        when(redisTemplate.keys(KEY_PATTERN)).thenReturn(null);

        // when
        List<String> activeStreamIds = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(activeStreamIds).isEmpty();
    }
}
