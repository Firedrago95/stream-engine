package io.slice.stream.engine.ingestion.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
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
    private static final String ANALYSIS_INDEX_KEY = "active:analysis:ids"; // ★ 검증을 위해 추가

    @BeforeEach
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void 새로운_스트림과_종료된_스트림을_정확히_반환하고_분석_인덱스를_업데이트해야_한다() throws Exception {
        // given
        // 기존 상태: channel1, channel3가 방송 중
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel3");
        stringRedisTemplate.opsForSet().add(ANALYSIS_INDEX_KEY, "channel1", "channel3");

        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "https://thumb.com/1.jpg", "소통", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "풍월량", "chat2", 200L, "title2", 1000, "https://thumb.com/2.jpg", "게임", Instant.EPOCH);

        // 현재 상태: channel1(유지), channel2(신규), channel3(종료)
        List<StreamTarget> currentStreams = List.of(streamTarget1, streamTarget2);

        // when
        StreamUpdateResults results = redisStreamRepository.update(currentStreams);

        // then
        Set<String> actualTargets = stringRedisTemplate.opsForSet().members(STREAM_TARGET_KEY);
        Set<String> actualAnalysisIds = stringRedisTemplate.opsForSet().members(ANALYSIS_INDEX_KEY);

        assertAll(
            // 1. 반환 결과 검증
            () -> assertThat(results.newStreamIds()).hasSize(1),
            () -> assertThat(results.newStreamIds().iterator().next().channelId()).isEqualTo("channel2"),
            () -> assertThat(results.closedStreamIds()).containsExactlyInAnyOrder("channel3"),

            // 2. Redis 내부 상태(Targets) 검증
            () -> assertThat(actualTargets).containsExactlyInAnyOrder("channel1", "channel2"),

            // 3. ★ 핵심: 분석 인덱스(Index on Write) 검증
            () -> assertThat(actualAnalysisIds).as("분석 인덱스에는 활성 상태인 channel1, channel2만 남아야 함")
                .containsExactlyInAnyOrder("channel1", "channel2"),
            () -> assertThat(actualAnalysisIds).doesNotContain("channel3")
        );
    }

    @Test
    void 모든_방송이_종료되면_관련_모든_키가_정리되어야_한다() {
        // given
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1");
        stringRedisTemplate.opsForSet().add(ANALYSIS_INDEX_KEY, "channel1");
        stringRedisTemplate.opsForHash().put(STREAM_LIVE_KEY, "channel1", "some-json");

        // 빈 리스트 전달 (모든 방송 종료 상황)
        List<StreamTarget> currentStreams = List.of();

        // when
        redisStreamRepository.update(currentStreams);

        // then
        assertAll(
            () -> assertThat(stringRedisTemplate.hasKey(STREAM_TARGET_KEY)).isFalse(),
            () -> assertThat(stringRedisTemplate.hasKey(ANALYSIS_INDEX_KEY)).isFalse(),
            () -> assertThat(stringRedisTemplate.hasKey(STREAM_LIVE_KEY)).isFalse()
        );
    }

    @Test
    void 스트림_정보를_정확히_업데이트해야_한다() throws Exception {
        // given
        stringRedisTemplate.opsForSet().add(STREAM_TARGET_KEY, "channel1", "channel3");
        StreamTarget streamTarget1 = new StreamTarget("channel1", "침착맨", "chat1", 100L, "title1", 1200, "https://thumb.com/1.jpg", "소통", Instant.EPOCH);
        StreamTarget streamTarget2 = new StreamTarget("channel2", "풍월량", "chat2", 200L, "title2", 1000, "https://thumb.com/2.jpg", "게임", Instant.EPOCH);
        List<StreamTarget> currentStreams = List.of(streamTarget1, streamTarget2);

        // when
        redisStreamRepository.update(currentStreams);

        // then
        String json1 = (String) stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY, "channel1");
        String json2 = (String) stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY, "channel2");
        Object channel3Data = stringRedisTemplate.opsForHash().get(STREAM_LIVE_KEY, "channel3");

        assertAll(
            () -> assertThat(json1).isNotNull(),
            () -> assertThat(json2).isNotNull(),
            () -> assertThat(objectMapper.readValue(json1, StreamTarget.class)).isEqualTo(streamTarget1),
            () -> assertThat(objectMapper.readValue(json2, StreamTarget.class)).isEqualTo(streamTarget2),
            () -> assertThat(channel3Data).isNull()
        );
    }
}
