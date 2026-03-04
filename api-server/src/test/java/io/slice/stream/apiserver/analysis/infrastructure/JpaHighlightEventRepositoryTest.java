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
        String streamId = "test-stream";
        Instant baseTime = Instant.now();

        // 과거 세션
        HighlightEventEntity oldSession = new HighlightEventEntity(streamId, baseTime.minus(1, ChronoUnit.HOURS), baseTime, 100L);
        // 최근 세션
        HighlightEventEntity recentSession = new HighlightEventEntity(streamId, baseTime, baseTime, 200L);

        repository.save(oldSession);
        repository.save(recentSession);

        // when
        Optional<HighlightEventEntity> result = repository.findOngoingSession(streamId, "ONGOING");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getStartTime()).isEqualTo(recentSession.getStartTime());
        assertThat(result.get().getPeakFirepower()).isEqualTo(200L);
    }

    @Test
    void 종료된_세션은_진행중인_세션_조회_시_포함되지_않는다() {
        // given
        String streamId = "test-stream-2";
        Instant baseTime = Instant.now();

        HighlightEventEntity finishedSession = new HighlightEventEntity(streamId, baseTime.minus(1, ChronoUnit.HOURS), baseTime, 100L);
        finishedSession.finish(baseTime.plus(10, ChronoUnit.SECONDS)); // 상태를 FINISHED로 변경

        repository.save(finishedSession);

        // when
        Optional<HighlightEventEntity> result = repository.findOngoingSession(streamId, "ONGOING");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 특정_날짜_범위의_하이라이트만_시작시간_순으로_조회한다() {
        // given
        String streamId = "test-stream";
        // 2026-03-04 데이터
        Instant target1 = Instant.parse("2026-03-04T10:00:00Z");
        Instant target2 = Instant.parse("2026-03-04T15:00:00Z");
        // 2026-03-03 데이터 (경계값 제외용)
        Instant otherDay = Instant.parse("2026-03-03T23:59:59Z");

        repository.save(new HighlightEventEntity(streamId, target2, target2.plusSeconds(10), 200L));
        repository.save(new HighlightEventEntity(streamId, target1, target1.plusSeconds(10), 100L));
        repository.save(new HighlightEventEntity(streamId, otherDay, otherDay.plusSeconds(10), 50L));

        Instant start = Instant.parse("2026-03-04T00:00:00Z");
        Instant end = Instant.parse("2026-03-05T00:00:00Z");

        // when
        List<HighlightEventEntity> results = repository.findAllByStreamIdAndDateRange(streamId, start, end);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStartTime()).isEqualTo(target1); // 정렬 확인 (ASC)
        assertThat(results.get(1).getStartTime()).isEqualTo(target2);
        assertThat(results).extracting(HighlightEventEntity::getStartTime).doesNotContain(otherDay);
    }
}
