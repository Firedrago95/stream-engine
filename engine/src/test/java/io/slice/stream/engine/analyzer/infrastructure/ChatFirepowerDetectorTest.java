package io.slice.stream.engine.analyzer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.analyzer.domain.ChatFirepowerStatus;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregation;
import io.slice.stream.engine.analyzer.domain.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.DetectionResult;
import io.slice.stream.engine.analyzer.domain.HighlightDetector; // HighlightDetector import
import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.engine.global.config.TimeConfig;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import({RedisConfig.class, ChatFirepowerDetector.class, TimeConfig.class, RedisChatRoomAggregationRepository.class}) // 이 줄은 그대로 둡니다. (ChatFirepowerDetector는 구현체로 Import가 필요합니다.)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest implements RedisTestSupport {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HighlightDetector detector;

    @Autowired
    private ChatRoomAggregationRepository repository;

    @Autowired
    private Clock clock;

    @Value("${highlight.range}")
    private Duration highlightRange;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    void 평상시에는_NORMAL_상태를_유지한다() {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);

        Instant now = clock.instant();
        long windowSeconds = highlightRange.toSeconds();
        long intervalSeconds = (long) (windowSeconds * 0.8 / 10);
        Instant startTime = now.minus(highlightRange).plusSeconds(windowSeconds / 10);

        for (int i = 0; i < 10; i++) {
            Instant currentTime = startTime.plusSeconds(i * intervalSeconds);
            aggregation.increaseCount(currentTime);
            aggregation.increaseCount(currentTime);
            repository.save(aggregation, currentTime);
        }

        // when
        DetectionResult detectionResult = detector.detect(roomId);

        // then
        assertThat(detectionResult.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
    }

    @Test
    void 평상시보다_채팅화력이_높으면_PEAK_상태를_유지한다() {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);
        Instant now = clock.instant();

        long windowSeconds = highlightRange.toSeconds();
        long intervalSeconds = (long) (windowSeconds * 0.8 / 10);
        Instant startTime = now.minus(highlightRange).plusSeconds(windowSeconds / 10);

        // 평균을 만들기 위해 평소 채팅량(2) 데이터를 쌓는다.
        for (int i = 0; i < 10; i++) {
            Instant currentTime = startTime.plusSeconds(i * intervalSeconds);
            aggregation.increaseCount(currentTime);
            aggregation.increaseCount(currentTime);
            repository.save(aggregation, currentTime);
        }

        // 화력이 폭발하는 시점의 데이터를 추가한다.
        Instant peakTime = now.minusMillis(500);
        for (int i = 0; i < 20; i++) {
            aggregation.increaseCount(peakTime);
        }
        repository.save(aggregation, peakTime);

        // when
        DetectionResult detectionResult = detector.detect(roomId);

        // then
        assertThat(detectionResult.status()).isEqualTo(ChatFirepowerStatus.PEAK);
    }

    @Test
    void 채팅_이력이_부족한_경우_WAITING_상태를_유지한다() {
        // given
        String roomId = "room1";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);
        Instant now = clock.instant();

        // 데이터가 MIN_DATA_POINTS(5개) 보다 적은 경우
        for (int i = 0; i < 4; i++) {
            Instant currentTime = now.minusSeconds(i * 2L);
            aggregation.increaseCount(currentTime);
            repository.save(aggregation, currentTime);
        }

        // when
        DetectionResult detectionResult = detector.detect(roomId);

        // then
        assertThat(detectionResult.status()).isEqualTo(ChatFirepowerStatus.WAITING);
    }
}
