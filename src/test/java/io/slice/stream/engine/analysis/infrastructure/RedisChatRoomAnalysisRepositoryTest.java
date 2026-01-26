package io.slice.stream.engine.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.engine.analysis.domain.ChatCountHistory;
import io.slice.stream.engine.analysis.domain.ChatCountHistory.DataPoint;
import io.slice.stream.engine.analysis.domain.ChatRoomAnalysis;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        chatRoomAnalysis.increaseCount();

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

    @Test
    void 채팅방별_채팅개수_기록을_조회한다() {
        // given
        Instant now = Instant.now();
        String streamId1 = "abcd1234";
        String streamId2 = "efgh5678";

        ChatRoomAnalysis chatRoomAnalysis1 = new ChatRoomAnalysis(streamId1);
        chatRoomAnalysis1.increaseCount();
        repository.save(chatRoomAnalysis1, now.minusSeconds(10));

        chatRoomAnalysis1.increaseCount();
        repository.save(chatRoomAnalysis1, now);

        ChatRoomAnalysis chatRoomAnalysis2 = new ChatRoomAnalysis(streamId2);
        chatRoomAnalysis2.increaseCount();
        repository.save(chatRoomAnalysis2, now);


        // when
        Optional<ChatCountHistory> chatCounts = repository.findByStreamId(streamId1);

        // then
        assertThat(chatCounts).isPresent();
        ChatCountHistory chatCountHistory = chatCounts.get();
        List<DataPoint> dataPoints = chatCountHistory.dataPoints();

        assertAll(
            () -> assertThat(chatCountHistory.streamId()).isEqualTo(streamId1),
            () -> assertThat(dataPoints.getFirst().timestamp()).isEqualTo(now.minusSeconds(10).toEpochMilli()),
            () -> assertThat(dataPoints.get(1).timestamp()).isEqualTo(now.toEpochMilli()),
            () -> assertThat(dataPoints.get(1).value()).isEqualTo(2)
        );
    }
}
