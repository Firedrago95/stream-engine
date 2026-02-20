package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregationRepository;
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.engine.global.config.TimeConfig;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import({RedisConfig.class, ChatFirepowerDetector.class, TimeConfig.class, RedisChatRoomAggregationRepository.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest implements RedisTestSupport {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatFirepowerDetector detector;

    @Autowired
    private ChatRoomAggregationRepository repository;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    void 평상시에는_NORMAL_상태를_유지한다() throws InterruptedException {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);

        for (int i = 0; i < 10; i++) {
            Instant now = Instant.now();
            aggregation.increaseCount(now);
            aggregation.increaseCount(now);
            repository.save(aggregation, now);
            Thread.sleep(10);
        }

        // when
        ChatFirepowerStatus status = detector.detect(roomId);

        // then
        assertThat(status).isEqualTo(ChatFirepowerStatus.NORMAL);
    }

    @Test
    void 평상시보다_채팅화력이_높으면_PEAK_상태를_유지한다() throws InterruptedException {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);

        for (int i = 0; i < 10; i++) {
            Instant now = Instant.now();
            aggregation.increaseCount(now);
            aggregation.increaseCount(now);
            repository.save(aggregation, now);
            Thread.sleep(10);
        }

        Instant peakTime = Instant.now();
        for (int i = 0; i < 20; i++) {
            aggregation.increaseCount(peakTime);
        }
        repository.save(aggregation, peakTime);

        // when
        ChatFirepowerStatus status = detector.detect(roomId);

        // then
        assertThat(status).isEqualTo(ChatFirepowerStatus.PEAK);
    }

    @Test
    void 채팅_이력이_부족한_경우_WAITING_상태를_유지한다() {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);

        for (int i = 0; i < 4; i++) {
            Instant now = Instant.now();
            aggregation.increaseCount(now);
            repository.save(aggregation, now);
        }

        // when
        ChatFirepowerStatus status = detector.detect(roomId);

        // then
        assertThat(status).isEqualTo(ChatFirepowerStatus.WAITING);
    }
}

