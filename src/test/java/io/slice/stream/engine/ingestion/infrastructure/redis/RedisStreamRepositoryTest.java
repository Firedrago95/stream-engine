package io.slice.stream.engine.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisStreamRepositoryTest implements RedisTestSupport {

    private static final String TEMP_KEY = "streams.active.temp";
    private static final String ACTUAL_KEY = "streams.active.id";
    private static final String INFO_KEY = "streams.info";

    @Autowired
    private RedisStreamRepository redisStreamRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, StreamTarget> redisTemplate;

    @BeforeEach
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
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
            () -> assertThat(results.newStreamIds()).containsExactly("channel2"),
            () -> assertThat(results.closedStreamIds()).containsExactly("channel3"),
            () -> assertThat(actualKeys).containsExactlyInAnyOrder("channel1", "channel2")
        );
    }

    @Test
    void 스트림_정보를_정확히_업데이트해야_한다() {
        // given
        stringRedisTemplate.opsForSet().add(ACTUAL_KEY, "channel1", "channel3");
        List<StreamTarget> currentStreams = List.of(
            new StreamTarget("channel1", "game1", 100, "title1", 1200),
            new StreamTarget("channel2", "game2", 200, "title2", 1000)
        );

        // when
        redisStreamRepository.update(currentStreams);

        // then
        Map<Object, Object> streamInfo = redisTemplate.opsForHash().entries(INFO_KEY);
        assertAll(
            () -> assertThat(streamInfo).hasSize(2),
            () -> assertThat(streamInfo.get("channel1")).isInstanceOf(StreamTarget.class),
            () -> assertThat(streamInfo.get("channel2")).isInstanceOf(StreamTarget.class),
            () -> assertThat(streamInfo).doesNotContainKey("channel3")
        );
    }
}
