package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.core.redis.Rediskeys;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisActiveStreamProviderTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<List> getActiveTargetsScript;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private RedisActiveStreamProvider redisActiveStreamProvider;

    @Test
    void getActiveStreamIds_활성화된_스트림_ID_목록을_반환한다() {
        // given
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        Set<String> mockIds = Set.of("id1", "id2");
        when(setOps.members(Rediskeys.ANALYSIS_INDEX)).thenReturn(mockIds);

        // when
        List<String> result = redisActiveStreamProvider.getActiveStreamIds();

        // then
        assertThat(result).containsExactlyInAnyOrder("id1", "id2");
    }

    @Test
    void getActiveStreamTargets_Lua_스크립트를_실행하여_객체_리스트를_반환한다() throws Exception {
        // given
        String json1 = "{\"channelId\":\"id1\"}";
        List<String> rawResults = List.of(json1);

        // Lua 스크립트 실행 결과 모킹
        when(redisTemplate.execute(eq(getActiveTargetsScript), anyList())).thenReturn(rawResults);

        // Jackson 역직렬화 모킹
        StreamTarget expectedTarget = new StreamTarget("id1", "name", "chat1", 1L, "title", 10, "url", "cat");
        when(jsonMapper.readValue(json1, StreamTarget.class)).thenReturn(expectedTarget);

        // when
        List<StreamTarget> result = redisActiveStreamProvider.getActiveStreamTargets();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).channelId()).isEqualTo("id1");
    }

    @Test
    void getActiveStreamTargets_스크립트_결과가_없으면_빈_리스트를_반환한다() {
        // given
        when(redisTemplate.execute(eq(getActiveTargetsScript), anyList())).thenReturn(List.of());

        // when
        List<StreamTarget> result = redisActiveStreamProvider.getActiveStreamTargets();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getActiveStreamTargets_역직렬화_실패_시_해당_항목은_제외하고_반환한다() throws Exception {
        // given
        String jsonNormal = "{\"channelId\":\"normal\"}";
        String jsonError = "{\"channelId\":\"error\"}";
        when(redisTemplate.execute(eq(getActiveTargetsScript), anyList())).thenReturn(List.of(jsonNormal, jsonError));

        StreamTarget normalTarget = new StreamTarget("normal", "n", "c", 1L, "t", 0, "u", "cat");
        when(jsonMapper.readValue(jsonNormal, StreamTarget.class)).thenReturn(normalTarget);
        when(jsonMapper.readValue(jsonError, StreamTarget.class)).thenThrow(new RuntimeException("fail"));

        // when
        List<StreamTarget> result = redisActiveStreamProvider.getActiveStreamTargets();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).channelId()).isEqualTo("normal");
    }
}
