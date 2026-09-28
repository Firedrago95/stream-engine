package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.core.redis.Rediskeys;
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import({RedisStreamerChatterRepository.class, RedisConfig.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisStreamerChatterRepositoryTest implements RedisTestSupport {

    @Autowired
    private RedisStreamerChatterRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void saveChatters_호출_시_유저_해시_Set이_정상_저장되고_TTL이_설정된다() {
        String streamId = "stream1";
        String yearWeek = "2026-W39";
        Set<Long> userHashes = Set.of(1001L, 1002L, 1003L);

        repository.saveChatters(streamId, userHashes, yearWeek);

        String redisKey = String.format(Rediskeys.STREAMER_CHATTERS_PREFIX, streamId, yearWeek);
        Set<String> members = redisTemplate.opsForSet().members(redisKey);
        Long expireSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

        assertAll(
            () -> assertThat(members).containsExactlyInAnyOrder("1001", "1002", "1003"),
            () -> assertThat(expireSeconds).isNotNull().isGreaterThan(0L)
        );
    }

    @Test
    void findChatters_단일_주차_조회_시_저장된_유저_해시_Set이_Long_타입으로_반환된다() {
        String streamId = "stream2";
        String yearWeek = "2026-W39";
        Set<Long> userHashes = Set.of(2001L, 2002L);
        repository.saveChatters(streamId, userHashes, yearWeek);

        Set<Long> foundChatters = repository.findChatters(streamId, yearWeek);

        assertThat(foundChatters).containsExactlyInAnyOrder(2001L, 2002L);
    }

    @Test
    void findChatters_다중_주차_조회_시_각_주차의_합집합으로_조회된다() {
        String streamId = "stream3";
        String week1 = "2026-W38";
        String week2 = "2026-W39";
        repository.saveChatters(streamId, Set.of(3001L, 3002L), week1);
        repository.saveChatters(streamId, Set.of(3002L, 3003L), week2);

        Set<Long> unionChatters = repository.findChatters(streamId, List.of(week1, week2));

        assertThat(unionChatters).containsExactlyInAnyOrder(3001L, 3002L, 3003L);
    }

    @Test
    void 빈_Set을_저장하면_아무런_Redis_명령도_수행하지_않는다() {
        String streamId = "stream4";
        String yearWeek = "2026-W39";

        repository.saveChatters(streamId, Set.of(), yearWeek);

        String redisKey = String.format(Rediskeys.STREAMER_CHATTERS_PREFIX, streamId, yearWeek);
        Boolean hasKey = redisTemplate.hasKey(redisKey);

        assertThat(hasKey).isFalse();
    }
}
