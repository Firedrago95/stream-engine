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
        Instant baseTime = Instant.now();
        long baseOffset = 3600000L; // 1시간 지점

        HighlightEventEntity oldSession = new HighlightEventEntity(
            streamId,
            baseTime.minus(1, ChronoUnit.HOURS),
            baseOffset - 3600000L,
            baseTime.minus(1, ChronoUnit.HOURS),
            baseOffset - 3600000L,
            100L
        );
        oldSession.finish(baseTime, baseOffset);

        HighlightEventEntity recentSession = new HighlightEventEntity(
            streamId,
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
        Instant baseTime = Instant.now();
        long baseOffset = 5000L;

        HighlightEventEntity finishedSession = new HighlightEventEntity(
            streamId,
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
    void 특정_날짜_범위의_하이라이트만_시작시간_순으로_조회한다() {
        // given
        String streamId = "test-stream-range";
        Instant target1 = Instant.parse("2026-03-04T10:00:00Z");
        Instant target2 = Instant.parse("2026-03-04T15:00:00Z");
        Instant otherDay = Instant.parse("2026-03-03T23:59:59Z");

        HighlightEventEntity event1 = new HighlightEventEntity(streamId, target2, 50000L, target2, 50000L, 200L);
        event1.finish(target2.plusSeconds(10), 60000L);

        HighlightEventEntity event2 = new HighlightEventEntity(streamId, target1, 10000L, target1, 10000L, 100L);
        event2.finish(target1.plusSeconds(10), 20000L);

        repository.save(event1);
        repository.save(event2);

        HighlightEventEntity eventOther = new HighlightEventEntity(streamId, otherDay, 0L, otherDay, 0L, 50L);
        eventOther.finish(otherDay.plusSeconds(10), 10000L);
        repository.save(eventOther);

        Instant start = Instant.parse("2026-03-04T00:00:00Z");
        Instant end = Instant.parse("2026-03-05T00:00:00Z");

        // when
        List<HighlightEventEntity> results = repository.findAllByStreamIdAndDateRange(streamId, start, end);

        // then
        assertThat(results).hasSize(2);
        // 시작 시간 기준 정렬 확인 (repository 쿼리에 정렬 조건이 있다면)
        assertThat(results.get(0).getStartTimeOffset()).isLessThan(results.get(1).getStartTimeOffset());
    }
}
