package io.slice.stream.engine.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@SpringBootTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisChatRoomAnalysisRepositoryTest implements RedisTestSupport {

    @Autowired
    private RedisChatRoomAnalysisRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<List> tsGetScript;

    private static final String CHAT_ANALYSIS_KEY = "chat:analysis:%s";

    @Test
    void 채팅갯수를_저장한다() {
        // given
        Instant now = Instant.now();
        String streamId = "abcd1234";
        ChatRoomAnalysis chatRoomAnalysis = new ChatRoomAnalysis(streamId);

        // when
        repository.save(chatRoomAnalysis, now);

        // then
        String key = String.format(CHAT_ANALYSIS_KEY, streamId);

        List<?> result = redisTemplate.execute(tsGetScript, List.of(key));

        assertThat(result).isNotNull().hasSize(2);

        long savedTimestamp = Long.parseLong(result.get(0).toString());
        long savedCount = Long.parseLong(result.get(1).toString());

        assertAll(
            () -> assertThat(savedCount).isEqualTo(chatRoomAnalysis.getCount()),
            () -> assertThat(savedTimestamp).isEqualTo(now.toEpochMilli())
        );
    }
}
