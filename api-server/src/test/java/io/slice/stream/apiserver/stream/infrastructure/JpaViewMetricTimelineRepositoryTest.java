package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class JpaViewMetricTimelineRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaViewMetricTimelineRepository timelineRepository;

    @Test
    void 세션_ID로_조회시_타임스탬프_오름차순으로_정렬되어_반환된다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant now = Instant.now();

        ViewMetricTimelineEntity p1 = new ViewMetricTimelineEntity(streamId, sessionId, now.minus(2, ChronoUnit.MINUTES), 100);
        ViewMetricTimelineEntity p2 = new ViewMetricTimelineEntity(streamId, sessionId, now.minus(1, ChronoUnit.MINUTES), 250);
        ViewMetricTimelineEntity p3 = new ViewMetricTimelineEntity(streamId, sessionId, now, 180);

        timelineRepository.saveAll(List.of(p2, p3, p1));

        List<ViewMetricTimelineEntity> result = timelineRepository.findBySessionIdOrderByTimestampAsc(sessionId);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getViewerCount()).isEqualTo(100);
        assertThat(result.get(1).getViewerCount()).isEqualTo(250);
        assertThat(result.get(2).getViewerCount()).isEqualTo(180);
    }

    @Test
    void 세션_ID로_평균_시청자수와_피크_시청자수를_정확하게_집계한다() {
        String streamId = "stream-2";
        String sessionId = "session-2";
        Instant now = Instant.now();

        ViewMetricTimelineEntity p1 = new ViewMetricTimelineEntity(streamId, sessionId, now.minus(2, ChronoUnit.MINUTES), 100);
        ViewMetricTimelineEntity p2 = new ViewMetricTimelineEntity(streamId, sessionId, now.minus(1, ChronoUnit.MINUTES), 200);
        ViewMetricTimelineEntity p3 = new ViewMetricTimelineEntity(streamId, sessionId, now, 300);

        timelineRepository.saveAll(List.of(p1, p2, p3));

        Double avgViewers = timelineRepository.findAverageViewerCountBySessionId(sessionId);
        Integer peakViewers = timelineRepository.findPeakViewerCountBySessionId(sessionId);

        assertThat(avgViewers).isEqualTo(200.0);
        assertThat(peakViewers).isEqualTo(300);
    }

    @Test
    void 데이터가_없는_세션의_경우_집계_결과가_null이다() {
        Double avgViewers = timelineRepository.findAverageViewerCountBySessionId("not-exist-session");
        Integer peakViewers = timelineRepository.findPeakViewerCountBySessionId("not-exist-session");

        assertThat(avgViewers).isNull();
        assertThat(peakViewers).isNull();
    }
}
