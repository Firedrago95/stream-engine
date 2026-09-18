package io.slice.stream.apiserver.category.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.category.infrastructure.JpaCategoryRankingRepository.CategoryRankingProjection;
import io.slice.stream.apiserver.category.infrastructure.entity.WeeklyCategoryRankingEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
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
class JpaCategoryRankingRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaCategoryRankingRepository rankingRepository;

    @Autowired
    private JpaWeeklyCategoryRankingRepository weeklyRankingRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 주간_카테고리별_누적_시청시간을_정확히_집계하고_내림차순_정렬한다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);
        double samplesPerHour = 120.0;

        createSegmentWithTimelines("stream-1", "sess-1", "메이플스토리", since.plus(1, ChronoUnit.HOURS), since.plus(3, ChronoUnit.HOURS), List.of(1200, 1200));
        createSegmentWithTimelines("stream-2", "sess-2", "리그 오브 레전드", since.plus(1, ChronoUnit.HOURS), since.plus(3, ChronoUnit.HOURS), List.of(2400, 2400));

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, samplesPerHour, 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCategoryName()).isEqualTo("리그 오브 레전드");
        assertThat(results.get(0).getExactHours()).isEqualTo(40L);
        assertThat(results.get(1).getCategoryName()).isEqualTo("메이플스토리");
        assertThat(results.get(1).getExactHours()).isEqualTo(20L);
    }

    @Test
    void 진행_중인_세그먼트의_시청자수도_집계에_포함된다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);

        StreamSessionSegmentEntity ongoingSegment = new StreamSessionSegmentEntity(
            "stream-ongoing", "sess-ongoing", "진행중 방송", "스타크래프트", since.plus(1, ChronoUnit.HOURS), 0L
        );
        em.persist(ongoingSegment);

        em.persist(new ViewMetricTimelineEntity("stream-ongoing", "sess-ongoing", since.plus(2, ChronoUnit.HOURS), 1200));
        em.flush();
        em.clear();

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, 120.0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategoryName()).isEqualTo("스타크래프트");
        assertThat(results.get(0).getExactHours()).isEqualTo(10L);
    }

    @Test
    void 세그먼트_시간_범위_밖의_타임라인_데이터는_집계에서_제외된다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);
        Instant segStart = since.plus(2, ChronoUnit.HOURS);
        Instant segEnd = since.plus(4, ChronoUnit.HOURS);

        StreamSessionSegmentEntity segment = new StreamSessionSegmentEntity(
            "stream-1", "sess-1", "방송", "발로란트", segStart, 0L
        );
        segment.endSegment(segEnd, 7200000L);
        em.persist(segment);

        em.persist(new ViewMetricTimelineEntity("stream-1", "sess-1", segStart.minusSeconds(60), 5000));
        em.persist(new ViewMetricTimelineEntity("stream-1", "sess-1", segStart.plusSeconds(60), 1200));
        em.persist(new ViewMetricTimelineEntity("stream-1", "sess-1", segEnd.plusSeconds(60), 5000));
        em.flush();
        em.clear();

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, 120.0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategoryName()).isEqualTo("발로란트");
        assertThat(results.get(0).getExactHours()).isEqualTo(10L);
    }

    @Test
    void talk_카테고리는_대소문자_무관하게_집계에서_제외된다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);

        createSegmentWithTimelines("stream-talk", "sess-talk", "Talk", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(3600));
        createSegmentWithTimelines("stream-game", "sess-game", "철권", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(1200));

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, 120.0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategoryName()).isEqualTo("철권");
    }

    @Test
    void 카테고리명이_null이거나_빈_문자열인_경우_집계에서_제외된다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);

        createSegmentWithTimelines("stream-null", "sess-null", null, since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(2400));
        createSegmentWithTimelines("stream-empty", "sess-empty", "", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(2400));
        createSegmentWithTimelines("stream-valid", "sess-valid", "로스트아크", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(1200));

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, 120.0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCategoryName()).isEqualTo("로스트아크");
    }

    @Test
    void limit_파라미터로_반환되는_카테고리_개수를_제한한다() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);

        createSegmentWithTimelines("s1", "sess-1", "게임1", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(3000));
        createSegmentWithTimelines("s2", "sess-2", "게임2", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(2000));
        createSegmentWithTimelines("s3", "sess-3", "게임3", since.plus(1, ChronoUnit.HOURS), since.plus(2, ChronoUnit.HOURS), List.of(1000));

        List<CategoryRankingProjection> results = rankingRepository.findWeeklyCategoryRankings(since, 120.0, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCategoryName()).isEqualTo("게임1");
        assertThat(results.get(1).getCategoryName()).isEqualTo("게임2");
    }

    @Test
    void 특정_주차의_주간_랭킹을_삭제하고_최신_주차를_조회한다() {
        LocalDate week1 = LocalDate.of(2026, 3, 2);
        LocalDate week2 = LocalDate.of(2026, 3, 9);

        WeeklyCategoryRankingEntity r1 = new WeeklyCategoryRankingEntity(week1, "게임A", 1, "1만 시간", 10000L, "same", 0, "🎮");
        WeeklyCategoryRankingEntity r2 = new WeeklyCategoryRankingEntity(week2, "게임B", 1, "2만 시간", 20000L, "up", 1, "🏆");
        WeeklyCategoryRankingEntity r3 = new WeeklyCategoryRankingEntity(week2, "게임C", 2, "1.5만 시간", 15000L, "down", 1, "🥈");

        weeklyRankingRepository.saveAll(List.of(r1, r2, r3));

        Optional<LocalDate> latestWeek = weeklyRankingRepository.findLatestWeekStartDate();
        assertThat(latestWeek).isPresent().contains(week2);

        weeklyRankingRepository.deleteByWeekStartDate(week1);
        em.flush();
        em.clear();

        List<WeeklyCategoryRankingEntity> week2Rankings = weeklyRankingRepository.findByWeekStartDateOrderByRankAsc(week2);
        assertThat(week2Rankings).hasSize(2);
        assertThat(week2Rankings.get(0).getCategoryName()).isEqualTo("게임B");
        assertThat(week2Rankings.get(1).getCategoryName()).isEqualTo("게임C");

        List<WeeklyCategoryRankingEntity> week1Rankings = weeklyRankingRepository.findByWeekStartDateOrderByRankAsc(week1);
        assertThat(week1Rankings).isEmpty();
    }

    private void createSegmentWithTimelines(
        String streamId,
        String sessionId,
        String categoryName,
        Instant startTime,
        Instant endTime,
        List<Integer> viewers
    ) {
        StreamSessionSegmentEntity segment = new StreamSessionSegmentEntity(
            streamId, sessionId, "제목", categoryName, startTime, 0L
        );
        segment.endSegment(endTime, ChronoUnit.MILLIS.between(startTime, endTime));
        em.persist(segment);

        long intervalSeconds = ChronoUnit.SECONDS.between(startTime, endTime) / (viewers.size() + 1);
        for (int i = 0; i < viewers.size(); i++) {
            Instant timestamp = startTime.plusSeconds((i + 1) * intervalSeconds);
            em.persist(new ViewMetricTimelineEntity(streamId, sessionId, timestamp, viewers.get(i)));
        }
        em.flush();
        em.clear();
    }
}
