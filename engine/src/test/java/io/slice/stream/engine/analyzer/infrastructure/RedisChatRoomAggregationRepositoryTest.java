package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.analyzer.domain.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.ChatAggregationResult.DataPoint;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregation;
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@DataRedisTest
@Import({RedisChatRoomAggregationRepository.class, RedisConfig.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class RedisChatRoomAggregationRepositoryTest implements RedisTestSupport {

    @Autowired
    private RedisChatRoomAggregationRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<List> tsGetScript;

    private static final String CHAT_AGGREGATION_KEY = "chat:aggregation:%s";

    @Test
    void 채팅갯수를_저장한다() {
        // given
        Instant now = Instant.now();
        String streamId = "abcd1234";
        ChatRoomAggregation chatRoomAggregation = new ChatRoomAggregation(streamId);
        chatRoomAggregation.increaseCount();

        // when
        repository.save(chatRoomAggregation, now);

        // then
        String key = String.format(CHAT_AGGREGATION_KEY, streamId);

        List<?> result = redisTemplate.execute(tsGetScript, List.of(key));

        assertThat(result).isNotNull().hasSize(2);

        long savedTimestamp = Long.parseLong(result.get(0).toString());
        long savedCount = Long.parseLong(result.get(1).toString());

        assertAll(
            () -> assertThat(savedCount).isEqualTo(chatRoomAggregation.getCount()),
            () -> assertThat(savedTimestamp).isEqualTo(now.toEpochMilli())
        );
    }

    @Test
    void 채팅방별_채팅개수_기록을_조회한다() {
        // given
        Instant now = Instant.now();
        String streamId1 = "abcd1234";
        String streamId2 = "efgh5678";

        ChatRoomAggregation chatRoomAggregation1 = new ChatRoomAggregation(streamId1);
        chatRoomAggregation1.increaseCount();
        repository.save(chatRoomAggregation1, now.minusSeconds(10));

        chatRoomAggregation1.increaseCount();
        repository.save(chatRoomAggregation1, now);

        ChatRoomAggregation chatRoomAggregation2 = new ChatRoomAggregation(streamId2);
        chatRoomAggregation2.increaseCount();
        repository.save(chatRoomAggregation2, now);


        // when
        Optional<ChatAggregationResult> chatCounts = repository.findByStreamId(streamId1);

        // then
        assertThat(chatCounts).isPresent();
        ChatAggregationResult chatAggregationResult = chatCounts.get();
        List<DataPoint> dataPoints = chatAggregationResult.dataPoints();

        assertAll(
            () -> assertThat(chatAggregationResult.streamId()).isEqualTo(streamId1),
            () -> assertThat(dataPoints.getFirst().timestamp()).isEqualTo(now.minusSeconds(10).toEpochMilli()),
            () -> assertThat(dataPoints.get(1).timestamp()).isEqualTo(now.toEpochMilli()),
            () -> assertThat(dataPoints.get(1).value()).isEqualTo(2)
        );
    }
}
