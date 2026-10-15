package io.slice.stream.collector.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.core.redis.Rediskeys;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class RedisTargetStreamReaderTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private JsonMapper jsonMapper;
    private RedisTargetStreamReader reader;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        given(redisTemplate.opsForHash()).willReturn(hashOperations);

        jsonMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

        reader = new RedisTargetStreamReader(redisTemplate, jsonMapper);
    }

    @Test
    @DisplayName("Redis stream:live: 해시에서 JSON 목록을 읽어와 StreamTarget 객체 목록으로 역직렬화한다")
    void readStreamTargetsFromRedisLiveHash() {
        String json1 = """
            {
                "channelId": "ch_1",
                "channelName": "침착맨",
                "chatChannelId": "chat_1",
                "liveId": 100,
                "liveTitle": "제목1",
                "concurrentUserCount": 5000,
                "adult": false
            }
            """;
        String json2 = """
            {
                "channelId": "ch_2",
                "channelName": "풍월량",
                "chatChannelId": "chat_2",
                "liveId": 200,
                "liveTitle": "제목2",
                "concurrentUserCount": 15000,
                "adult": false
            }
            """;

        given(hashOperations.values(Rediskeys.STREAM_LIVE_HASH)).willReturn(List.of(json1, json2));

        List<StreamTarget> targets = reader.getTargetStreams();

        assertThat(targets).hasSize(2);
        assertThat(targets.get(0).channelId()).isEqualTo("ch_1");
        assertThat(targets.get(0).chatChannelId()).isEqualTo("chat_1");
        assertThat(targets.get(1).channelId()).isEqualTo("ch_2");
        assertThat(targets.get(1).chatChannelId()).isEqualTo("chat_2");
    }

    @Test
    @DisplayName("Redis에 데이터가 없거나 비어있으면 빈 리스트를 반환한다")
    void returnEmptyListWhenRedisValuesIsEmptyOrNull() {
        given(hashOperations.values(Rediskeys.STREAM_LIVE_HASH)).willReturn(List.of());

        List<StreamTarget> targets = reader.getTargetStreams();

        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("손상된 JSON 데이터가 섞여 있어도 건너뛰고 정상 데이터만 반환한다")
    void skipCorruptedJsonAndReturnValidTargetsOnly() {
        String validJson = """
            {
                "channelId": "ch_valid",
                "channelName": "옥냥이",
                "chatChannelId": "chat_valid",
                "liveId": 300,
                "liveTitle": "게임",
                "concurrentUserCount": 3000,
                "adult": false
            }
            """;
        String invalidJson = "{ corrupt_json ... }";

        given(hashOperations.values(Rediskeys.STREAM_LIVE_HASH)).willReturn(List.of(validJson, invalidJson));

        List<StreamTarget> targets = reader.getTargetStreams();

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).channelId()).isEqualTo("ch_valid");
    }

    @Test
    @DisplayName("Redis 조회 중 예외가 발생하면 IllegalStateException으로 래핑하여 전파한다")
    void throwIllegalStateExceptionWhenRedisFails() {
        given(hashOperations.values(Rediskeys.STREAM_LIVE_HASH)).willThrow(new RuntimeException("Redis connection error"));

        assertThatThrownBy(() -> reader.getTargetStreams())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("[Redis 타겟 스트림 목록 조회 실패]");
    }
}
