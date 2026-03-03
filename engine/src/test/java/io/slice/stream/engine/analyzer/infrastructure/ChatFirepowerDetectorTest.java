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
        String roomId = "stable_room";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);
        Instant now = clock.instant();

        // 충분한 표본(15개)을 넣고, 변화량을 일정하게 유지
        for (int i = 0; i < 15; i++) {
            Instant t = now.minusSeconds(i * 3L);
            aggregation.increaseCount(t); // 매 버킷당 채팅 1개씩
            repository.save(aggregation, t);
        }

        // when
        DetectionResult result = detector.detect(roomId);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
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

        // 분석에 필요한 최소 포인트가 10개라면, 9개까지만 넣어서 테스트
        for (int i = 0; i < 9; i++) {
            Instant currentTime = now.minusSeconds(i * 3L);
            aggregation.increaseCount(currentTime);
            repository.save(aggregation, currentTime);
        }

        // when
        DetectionResult result = detector.detect(roomId);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.WAITING);
    }

    @Test
    void 최소_화력_임계치보다_낮으면_아무리_상대적_배수가_높아도_NORMAL을_반환한다() {
        // given
        String roomId = "quiet_room";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);
        Instant now = clock.instant();

        // 평소 채팅량 0.1개 수준
        for (int i = 0; i < 15; i++) {
            repository.save(aggregation, now.minusSeconds(i * 3L));
        }

        // 갑자기 채팅 2개 발생
        aggregation.increaseCount(now);
        aggregation.increaseCount(now);
        repository.save(aggregation, now);

        // when
        DetectionResult result = detector.detect(roomId);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.NORMAL);
        assertThat(result.firepower()).isEqualTo(2L);
    }

    @Test
    void 통계적으로_유의미한_폭발인_경우_ZScore_판정에_의해_PEAK를_반환한다() {
        // given
        String roomId = "burst_room";
        ChatRoomAggregation aggregation = new ChatRoomAggregation(roomId, Instant.EPOCH);
        Instant now = clock.instant();

        // 과거(33초 전)부터 현재(3초 전)까지 3초 단위로 데이터 적재
        for (int i = 11; i >= 1; i--) {
            Instant t = now.minusSeconds(i * 3L);
            // 평소 화력 5
            for(int j=0; j<5; j++) aggregation.increaseCount(t);
            repository.save(aggregation, t);
        }

        // 현재 시점: 화력 30으로 폭발
        for(int j=0; j<30; j++) aggregation.increaseCount(now);
        repository.save(aggregation, now);

        // when
        DetectionResult result = detector.detect(roomId);

        // then
        assertThat(result.status()).isEqualTo(ChatFirepowerStatus.PEAK);
    }
}
