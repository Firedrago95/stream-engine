package io.slice.stream.engine.highlight.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.global.config.RedisConfig;
import io.slice.stream.engine.global.config.TimeConfig;
import io.slice.stream.engine.highlight.domain.ChatFirepowerStatus;
import io.slice.stream.testcontainer.redis.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@DataRedisTest
@Import({RedisConfig.class, ChatFirepowerDetector.class, TimeConfig.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatFirepowerDetectorTest implements RedisTestSupport {

    private static final String CHAT_ANALYSIS_KEY = "chat:analysis:%s";
    private static final long RETENTION = 604_800_000;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<Long> tsAddScript;

    @Autowired
    private ChatFirepowerDetector detector;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    void 평상시에는_NORMAL_상태를_유지한다() throws InterruptedException {
        // given
        String roomId = "room1";
        String key = String.format(CHAT_ANALYSIS_KEY, roomId);
        String retention = String.valueOf(RETENTION);

        for (int i = 0; i < 10; i++) {
            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            redisTemplate.execute(tsAddScript, List.of(key), timestamp, "2", retention);
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
        String key = String.format(CHAT_ANALYSIS_KEY, roomId);
        String retention = String.valueOf(RETENTION);

        // 평균을 만들기 위해 평소 채팅량(2) 데이터를 쌓는다.
        for (int i = 0; i < 10; i++) {
            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            redisTemplate.execute(tsAddScript, List.of(key), timestamp, "2", retention);
            Thread.sleep(10);
        }

        // 화력이 폭발하는 시점의 데이터를 추가한다.
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        redisTemplate.execute(tsAddScript, List.of(key),timestamp, "20", retention); // 폭발 시점 채팅량: 20

        // when
        ChatFirepowerStatus status = detector.detect(roomId);

        // then
        assertThat(status).isEqualTo(ChatFirepowerStatus.PEAK);
    }

    @Test
    void 채팅_이력이_부족한_경우_WAITING_상태를_유지한다() {
        // given
        String roomId = "room1";
        String key = String.format(CHAT_ANALYSIS_KEY, roomId);
        String retention = String.valueOf(RETENTION);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        // 데이터가 MIN_DATA_POINTS(5개) 보다 적은 경우
        redisTemplate.execute(tsAddScript, List.of(key), timestamp, "2", retention);

        // when
        ChatFirepowerStatus status = detector.detect(roomId);

        // then
        assertThat(status).isEqualTo(ChatFirepowerStatus.WAITING);
    }
}

