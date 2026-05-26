package io.slice.stream.engine.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

@DataRedisTest
@Import({RedisStreamRepository.class, RedisConfig.class, JacksonAutoConfiguration.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisStreamRepositoryTest implements RedisTestSupport {

    @Autowired
    private RedisStreamRepository redisStreamRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String STREAM_TARGET_KEY = "stream:targets";
    private static final String STREAM_LIVE_KEY = "stream:live:";
    private static final String ANALYSIS_INDEX_KEY = "active:analysis:ids";

    @BeforeEach
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 활성_방송_ID_목록을_조회할_수_있어야_한다() {
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel2");

        Set<String> activeChannelIds = redisStreamRepository.getActiveChannelIds();

        assertThat(activeChannelIds).containsExactlyInAnyOrder("channel1", "channel2");
    }

    @Test
    void 다중_채널의_상세_정보를_조회하고_존재하지_않는_것은_필터링해야_한다() throws Exception {
        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", Instant.EPOCH);
        String json1 = objectMapper.writeValueAsString(streamTarget1);
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY, "channel1", json1);

        List<StreamTarget> targets = redisStreamRepository.getStreamTargets(List.of("channel1", "channel2"));

        assertAll(
            () -> assertThat(targets).hasSize(1),
            () -> assertThat(targets.get(0).channelId()).isEqualTo("channel1")
        );
    }

    @Test
    void 변경사항을_동기화하고_종료된_방송을_제거한_뒤_활성_방송을_등록해야_한다() throws Exception {
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel3");
        stringRedisTemplate.opsForSet().add(ANALYSIS_INDEX_KEY, "channel1", "channel3");
        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "thumb1.jpg", "소통", Instant.EPOCH);
        String json1 = objectMapper.writeValueAsString(streamTarget1);
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY, "channel1", json1);
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY, "channel3", "some-json-3");

        StreamTarget streamTarget2 = new StreamTarget("channel2", "풍월량", "chat2", 200L, "title2", 1000, "thumb2.jpg", "게임", Instant.EPOCH);

        redisStreamRepository.sync(Set.of("channel3"), List.of(streamTarget1, streamTarget2));

        Set<String> actualTargets = stringRedisTemplate.opsForSet().members(STREAM_TARGET_KEY);
        Set<String> actualAnalysisIds = stringRedisTemplate.opsForSet().members(ANALYSIS_INDEX_KEY);
        Object channel3Data = stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY, "channel3");

        assertAll(
            () -> assertThat(actualTargets).containsExactlyInAnyOrder("channel1", "channel2"),
            () -> assertThat(actualAnalysisIds).containsExactlyInAnyOrder("channel1", "channel2"),
            () -> assertThat(channel3Data).isNull()
        );
    }

    @Test
    void 활성_방송이_없을_경우_모든_관련_키가_삭제되어야_한다() {
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1");
        stringRedisTemplate.opsForSet().add(ANALYSIS_INDEX_KEY, "channel1");
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY, "channel1", "some-json");

        redisStreamRepository.sync(Set.of("channel1"), List.of());

        assertAll(
            () -> assertThat(stringRedisTemplate.hasKey(STREAM_TARGET_KEY)).isFalse(),
            () -> assertThat(stringRedisTemplate.hasKey(ANALYSIS_INDEX_KEY)).isFalse(),
            () -> assertThat(stringRedisTemplate.hasKey(STREAM_LIVE_KEY)).isFalse()
        );
    }
}
