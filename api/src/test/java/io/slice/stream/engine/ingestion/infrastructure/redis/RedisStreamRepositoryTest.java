package io.slice.stream.engine.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisStreamRepositoryTest implements RedisTestSupport {

    @Autowired
    private RedisStreamRepository redisStreamRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper; // 주입받아서 검증 시 사용

    private static final String STREAM_TARGET_KEY = "stream:targets";
    private static final String STREAM_LIVE_KEY_PREFIX = "stream:live:";

    @BeforeEach
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_정확히_반환해야_한다() throws Exception {
        // given
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel3");

        StreamTarget streamTarget1 = new StreamTarget("channel1", "game1", "chat1", 100L, "title1", 1200);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "game2", "chat2", 200L, "title2", 1000);

        // Redis에 미리 저장될 StreamTarget 정보 (HMSET으로 Lua 스크립트가 참조할 데이터)
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY_PREFIX, streamTarget1.channelId(), objectMapper.writeValueAsString(streamTarget1));
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY_PREFIX, streamTarget2.channelId(), objectMapper.writeValueAsString(streamTarget2));

        List<StreamTarget> currentStreams = List.of(streamTarget1, streamTarget2);

        // when
        StreamUpdateResults results = redisStreamRepository.update(currentStreams);

        // then
        Set<String> actualKeys = stringRedisTemplate.opsForSet().members(STREAM_TARGET_KEY);
        assertAll(
            () -> assertThat(results.newStreamIds()).containsExactlyInAnyOrder(streamTarget2), // 순서 보장 안될 수 있음
            () -> assertThat(results.closedStreamIds()).containsExactlyInAnyOrder("channel3"),
            () -> assertThat(actualKeys).containsExactlyInAnyOrder("channel1", "channel2")
        );
    }

    @Test
    void 스트림_정보를_정확히_업데이트해야_한다() throws Exception {
        // given
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel3");
        StreamTarget streamTarget1 = new StreamTarget("channel1", "game1", "chat1", 100L, "title1", 1200);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "game2", "chat2", 200L, "title2", 1000);
        List<StreamTarget> currentStreams = List.of(streamTarget1, streamTarget2);

        // when
        redisStreamRepository.update(currentStreams);

        // then
        String json1 = (String) stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY_PREFIX, "channel1");
        String json2 = (String) stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY_PREFIX, "channel2");
        Object channel3 = stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY_PREFIX, "channel3");

        assertAll(
            () -> assertThat(json1).isNotNull(),
            () -> assertThat(json2).isNotNull(),
            () -> assertThat(objectMapper.readValue(json1, StreamTarget.class)).isEqualTo(streamTarget1),
            () -> assertThat(objectMapper.readValue(json2, StreamTarget.class)).isEqualTo(streamTarget2),
            () -> assertThat(channel3).isNull()
        );
    }
}
