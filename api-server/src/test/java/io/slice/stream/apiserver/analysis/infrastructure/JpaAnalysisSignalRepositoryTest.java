package io.slice.stream.apiserver.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import jakarta.persistence.EntityManager;
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
class JpaAnalysisSignalRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaAnalysisSignalRepository jpaRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 기준_시간_이전의_데이터를_1분_단위로_압축하여_요약_테이블에_저장한다() {
        // given
        String streamId = "test-stream";
        Instant baseTime = Instant.parse("2024-01-01T10:00:00Z");

        // 10:00:05 (Offset: 5s)
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plusSeconds(5), 100L, 5000L));
        // 10:00:40 (Offset: 40s)
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plusSeconds(40), 200L, 40000L));
        // 10:01:05 (Offset: 65s)
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plus(1, ChronoUnit.MINUTES), 300L, 65000L));

        Instant cutoffTime = baseTime.plus(5, ChronoUnit.MINUTES);

        // when
        int affectedRows = jpaRepository.rollupOldSignals(cutoffTime);

        // then
        assertThat(affectedRows).isEqualTo(2);

        Object[] summary = (Object[]) em.createNativeQuery(
                "SELECT firepower_avg, firepower_max, offset_ms FROM analysis_signals_summary WHERE timestamp_minute = '2024-01-01 10:00:00+00'")
            .getSingleResult();

        assertThat(((Number) summary[0]).longValue()).isEqualTo(150L); // 평균
        assertThat(((Number) summary[1]).longValue()).isEqualTo(200L); // 최대값
        // 5000L와 40000L 중 최소값인 5000L이 나와야 함
        assertThat(((Number) summary[2]).longValue()).isEqualTo(5000L);
    }

    @Test
    void 기준_시간_이전의_원본_데이터만_선택적으로_삭제한다() {
        // given
        Instant now = Instant.now();
        Instant oldTime = now.minus(4, ChronoUnit.DAYS);
        Instant recentTime = now.minus(1, ChronoUnit.DAYS);

        jpaRepository.save(new AnalysisSignalEntity("stream-old", "NORMAL", oldTime, 100L, 0L));
        jpaRepository.save(new AnalysisSignalEntity("stream-recent", "NORMAL", recentTime, 200L, 86400000L));

        Instant cutoffTime = now.minus(3, ChronoUnit.DAYS);

        // when
        int deletedCount = jpaRepository.deleteOlderThan(cutoffTime);

        // then
        assertThat(deletedCount).isEqualTo(1);

        List<AnalysisSignalEntity> remaining = jpaRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getStreamId()).isEqualTo("stream-recent");
    }
}
