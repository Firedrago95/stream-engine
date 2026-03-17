package io.slice.stream.apiserver.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JpaHighlightEventRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaHighlightEventRepository repository;

    @Test
    void 특정_스트림의_진행중인_세션을_가장_최근_시작시간_기준으로_조회한다() {
        // given
        String streamId = "test-stream-session";
        String sessionId = "sessionId";
        Instant baseTime = Instant.now();
        long baseOffset = 3600000L; // 1시간 지점

        HighlightEventEntity oldSession = new HighlightEventEntity(
            streamId,
            sessionId,
            baseTime.minus(1, ChronoUnit.HOURS),
            baseOffset - 3600000L,
            baseTime.minus(1, ChronoUnit.HOURS),
            baseOffset - 3600000L,
            100L
        );
        oldSession.finish(baseTime, baseOffset);

        HighlightEventEntity recentSession = new HighlightEventEntity(
            streamId,
            sessionId,
            baseTime,
            baseOffset,
            baseTime,
            baseOffset,
            200L
        );

        repository.save(oldSession);
        repository.save(recentSession);

        // when
        Optional<HighlightEventEntity> result = repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getPeakFirepower()).isEqualTo(200L);
        assertThat(result.get().getStartTimeOffset()).isEqualTo(baseOffset);
    }

    @Test
    void 종료된_세션은_진행중인_세션_조회_시_포함되지_않는다() {
        // given
        String streamId = "test-stream-2";
        String sessionId = "sessionId";
        Instant baseTime = Instant.now();
        long baseOffset = 5000L;

        HighlightEventEntity finishedSession = new HighlightEventEntity(
            streamId,
            sessionId,
            baseTime.minus(1, ChronoUnit.HOURS),
            0L,
            baseTime.minus(1, ChronoUnit.HOURS),
            0L,
            100L
        );

        finishedSession.finish(baseTime.plus(10, ChronoUnit.SECONDS), baseOffset + 10000L);

        repository.save(finishedSession);

        // when
        Optional<HighlightEventEntity> result = repository.findFirstByStreamIdAndStatusOrderByStartTimeDesc(streamId, "ONGOING");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 임계시간_이전의_ONGOING_좀비세션만_조회한다() {
        // given
        Instant now = Instant.now();
        Instant threshold = now.minus(3, ChronoUnit.MINUTES);

        // 좀비 세션
        HighlightEventEntity zombie = new HighlightEventEntity(
            "zombie-stream", "session-1", now.minus(4, ChronoUnit.MINUTES), 1000L, now.minus(4, ChronoUnit.MINUTES), 1000L, 100L);
        repository.save(zombie);

        // 정상 세션
        HighlightEventEntity active = new HighlightEventEntity(
            "active-stream", "session-2",now.minus(1, ChronoUnit.MINUTES), 2000L, now.minus(1, ChronoUnit.MINUTES), 2000L, 200L);
        repository.save(active);

        // 종료된 세션
        HighlightEventEntity finished = new HighlightEventEntity(
            "finished-stream", "session-3",now.minus(5, ChronoUnit.MINUTES), 0L, now.minus(5, ChronoUnit.MINUTES), 0L, 50L);
        finished.finish(now.minus(4, ChronoUnit.MINUTES), 10000L);
        repository.save(finished);

        // when
        List<HighlightEventEntity> results = repository.findZombieSessions(threshold);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStreamId()).isEqualTo("zombie-stream");
        assertThat(results.get(0).getStatus()).isEqualTo("ONGOING");
    }

    @Test
    void 특정_세션의_하이라이트만_시작시간_순으로_조회한다() {
        // given
        String streamId = "test-stream-session-query";
        String targetSession = "target-session";
        String otherSession = "other-session";
        Instant baseTime = Instant.now();

        // 타겟 세션의 하이라이트 2개 (시작 시간 다르게 세팅)
        // event1: 늦게 시작한 하이라이트
        HighlightEventEntity event1 = new HighlightEventEntity(
            streamId, targetSession, baseTime.plusSeconds(10), 10000L, baseTime.plusSeconds(10), 10000L, 200L
        );
        event1.finish(baseTime.plusSeconds(60), 60000L);

        // event2: 일찍 시작한 하이라이트
        HighlightEventEntity event2 = new HighlightEventEntity(
            streamId, targetSession, baseTime, 0L, baseTime, 0L, 100L
        );
        event2.finish(baseTime.plusSeconds(30), 30000L);
        repository.save(event1);
        repository.save(event2);

        // 다른 세션의 하이라이트 (조회되면 안 됨)
        HighlightEventEntity eventOther = new HighlightEventEntity(
            streamId, otherSession, baseTime, 0L, baseTime, 0L, 50L
        );
        repository.save(eventOther);

        // when
        List<HighlightEventEntity> results = repository.findAllByStreamIdAndSessionId(streamId, targetSession);

        // then
        assertThat(results).hasSize(2);
        // startTime ASC (오름차순) 정렬이 제대로 되었는지 확인 (일찍 시작한 event2가 먼저 와야 함)
        assertThat(results.get(0).getPeakFirepower()).isEqualTo(100L);
        assertThat(results.get(1).getPeakFirepower()).isEqualTo(200L);
    }

    @Test
    void 특정_세션의_하이라이트_중_화력_상위_10개만_남기고_나머지는_삭제한다() {
        // given
        String streamId = "cleanup-stream";
        String sessionId = "cleanup-session";
        Instant now = Instant.now();

        // 1. 하이라이트 15개를 생성 (화력 100~1500까지 100단위)
        for (int i = 1; i <= 15; i++) {
            HighlightEventEntity hl = new HighlightEventEntity(
                streamId, sessionId, now.plusSeconds(i), (long) i * 1000,
                now.plusSeconds(i), (long) i * 1000, (long) i * 100
            );
            hl.finish(now.plusSeconds(i + 5), (long) (i + 5) * 1000);
            repository.save(hl);
        }

        // 2. 다른 세션 데이터 (지워지면 안 됨)
        HighlightEventEntity otherHl = new HighlightEventEntity(
            streamId, "other-session", now, 0L, now, 0L, 9999L
        );
        otherHl.finish(now.plusSeconds(10), 10000L);
        repository.save(otherHl);

        // persist 후 DB 반영
        repository.flush();

        // when
        int deletedCount = repository.deleteExceptTop10(sessionId);

        // then
        assertThat(deletedCount).isEqualTo(5); // 15개 중 10개 남기고 5개 삭제됨

        List<HighlightEventEntity> remaining = repository.findAllByStreamIdAndSessionId(streamId, sessionId);
        assertThat(remaining).hasSize(10);

        // 남은 것 중 가장 화력이 낮은게 600L(6번째)인지 확인 (상위 10개: 1500~600)
        long minFirepower = remaining.stream()
            .mapToLong(HighlightEventEntity::getPeakFirepower)
            .min().orElse(0);
        assertThat(minFirepower).isEqualTo(600L);

        // 다른 세션 데이터는 그대로 있는지 확인
        assertThat(repository.findAllByStreamIdAndSessionId(streamId, "other-session")).hasSize(1);
    }
}
