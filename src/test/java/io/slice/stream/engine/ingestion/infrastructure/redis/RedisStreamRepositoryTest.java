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

    private static final String ACTUAL_KEY = "streams.active.id";
    private static final String INFO_KEY = "streams.info";

    @BeforeEach
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_정확히_반환해야_한다() {
        // given
        stringRedisTemplate.opsForSet().add(ACTUAL_KEY, "channel1", "channel3");

        List<StreamTarget> currentStreams = List.of(
            new StreamTarget("channel1", "game1", 100, "title1", 1200),
            new StreamTarget("channel2", "game2", 200, "title2", 1000)
        );

        // when
        StreamUpdateResults results = redisStreamRepository.update(currentStreams);

        // then
        Set<String> actualKeys = stringRedisTemplate.opsForSet().members(ACTUAL_KEY);
        assertAll(
            () -> assertThat(results.newStreamIds()).containsExactlyInAnyOrder("channel2"), // 순서 보장 안될 수 있음
            () -> assertThat(results.closedStreamIds()).containsExactlyInAnyOrder("channel3"),
            () -> assertThat(actualKeys).containsExactlyInAnyOrder("channel1", "channel2")
        );
    }

    @Test
    void 스트림_정보를_정확히_업데이트해야_한다() throws Exception {
        // given
        stringRedisTemplate.opsForSet().add(ACTUAL_KEY, "channel1", "channel3");
        List<StreamTarget> currentStreams = List.of(
            new StreamTarget("channel1", "game1", 100, "title1", 1200),
            new StreamTarget("channel2", "game2", 200, "title2", 1000)
        );

        // when
        redisStreamRepository.update(currentStreams);

        // then
        // 직접 String으로 꺼내서 역직렬화 검증
        String json1 = (String) stringRedisTemplate.opsForHash().get(INFO_KEY, "channel1");
        String json2 = (String) stringRedisTemplate.opsForHash().get(INFO_KEY, "channel2");
        Object channel3 = stringRedisTemplate.opsForHash().get(INFO_KEY, "channel3");

        assertAll(
            () -> assertThat(json1).isNotNull(),
            () -> assertThat(json2).isNotNull(),
            () -> assertThat(objectMapper.readValue(json1, StreamTarget.class).channelName()).isEqualTo("game1"),
            () -> assertThat(channel3).isNull()
        );
    }
}
