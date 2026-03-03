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

        // 10:00:05와 10:00:40에 데이터 저장
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plusSeconds(5), 100L));
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plusSeconds(40), 200L));

        // 10:01:05에 데이터 저장
        jpaRepository.save(new AnalysisSignalEntity(streamId, "NORMAL", baseTime.plus(1, ChronoUnit.MINUTES), 300L));

        Instant cutoffTime = baseTime.plus(5, ChronoUnit.MINUTES);

        // when
        int affectedRows = jpaRepository.rollupOldSignals(cutoffTime);

        // then
        // 1. 10:00분 데이터 2건이 1줄로, 10:01분 데이터 1건이 1줄로 합쳐져 총 2행이 삽입되어야 함
        assertThat(affectedRows).isEqualTo(2);

        // 2. 요약 테이블 직접 조회 검증 (10:00분 데이터의 평균/최대값 확인)
        Object[] summary = (Object[]) em.createNativeQuery(
                "SELECT firepower_avg, firepower_max FROM analysis_signals_summary WHERE timestamp_minute = '2024-01-01 10:00:00+00'")
            .getSingleResult();

        assertThat(((Number) summary[0]).longValue()).isEqualTo(150L); // (100 + 200) / 2
        assertThat(((Number) summary[1]).longValue()).isEqualTo(200L); // max(100, 200)
    }

    @Test
    void 기준_시간_이전의_원본_데이터만_선택적으로_삭제한다() {
        // given
        Instant now = Instant.now();
        Instant oldTime = now.minus(4, ChronoUnit.DAYS); // 3일보다 더 과거
        Instant recentTime = now.minus(1, ChronoUnit.DAYS); // 3일 이내

        jpaRepository.save(new AnalysisSignalEntity("stream-old", "NORMAL", oldTime, 100L));
        jpaRepository.save(new AnalysisSignalEntity("stream-recent", "NORMAL", recentTime, 200L));

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
