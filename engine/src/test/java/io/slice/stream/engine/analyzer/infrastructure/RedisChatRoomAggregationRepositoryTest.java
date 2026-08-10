package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatAggregationResult.DataPoint;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregation;
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
        ChatRoomAggregation chatRoomAggregation = new ChatRoomAggregation(streamId, now);
        chatRoomAggregation.increaseCount(now, false);

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

        ChatRoomAggregation chatRoomAggregation1 = new ChatRoomAggregation(streamId1, now.minusSeconds(10));
        chatRoomAggregation1.increaseCount(now.minusSeconds(10), false);
        repository.save(chatRoomAggregation1, now.minusSeconds(10));

        chatRoomAggregation1.increaseCount(now, false);
        repository.save(chatRoomAggregation1, now);

        ChatRoomAggregation chatRoomAggregation2 = new ChatRoomAggregation(streamId2, now);
        chatRoomAggregation2.increaseCount(now, false);
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

    @Test
    void 데이터_누락_구간이_발생하면_중간_이빨을_0으로_채워서_반환한다() {
        // given
        Instant now = Instant.now();
        String streamId = "sparse_room";

        // T=0 (채팅 10개 누적)
        ChatRoomAggregation agg1 = new ChatRoomAggregation(streamId, now);
        for(int i=0; i<10; i++) agg1.increaseCount(now, false);
        repository.save(agg1, now);

        // T=3초 (채팅 2개 추가, 누적 12개) -> 정상 델타: 2
        Instant t1 = now.plusSeconds(3);
        agg1.increaseCount(t1, false);
        agg1.increaseCount(t1, false);
        repository.save(agg1, t1);

        // T=12초 (T=6초, T=9초 구간은 채팅이 없어 저장되지 않음 -> 공백 발생)
        // (채팅 3개 추가, 누적 15개) -> 이전 기록과의 시간 차이가 9초이므로 누락 틱은 2개(0, 0), 마지막 델타는 3
        Instant t2 = now.plusSeconds(12);
        agg1.increaseCount(t2, false);
        agg1.increaseCount(t2, false);
        agg1.increaseCount(t2, false);
        repository.save(agg1, t2);

        // when
        List<Long> deltas = repository.getFirepowerDeltas(streamId, now.minusSeconds(1), now.plusSeconds(15));

        // then
        // T=3초의 델타: 2
        // T=6초의 패딩: 0
        // T=9초의 패딩: 0
        // T=12초의 델타: 3
        assertThat(deltas).containsExactly(2L, 0L, 0L, 3L);
    }
}
