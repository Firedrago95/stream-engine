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
    void 방송시간을_기준으로_1분_단위_버킷을_생성하여_요약_테이블에_압축_저장한다() {
        // given
        String streamId = "test-stream";
        Instant baseTime = Instant.parse("2024-01-01T10:00:00Z");

        // 10:00:05 (Offset: 5s) -> [0ms 바구니]
        jpaRepository.save(new AnalysisSignalEntity(streamId, "sessionId","NORMAL", baseTime.plusSeconds(5), 100L, 5000L));
        // 10:00:40 (Offset: 40s) -> [0ms 바구니]
        jpaRepository.save(new AnalysisSignalEntity(streamId, "sessionId","NORMAL", baseTime.plusSeconds(40), 200L, 40000L));
        // 10:01:05 (Offset: 65s) -> [6000ms 바구니]
        jpaRepository.save(new AnalysisSignalEntity(streamId, "sessionId","NORMAL", baseTime.plusSeconds(65), 300L, 65000L));

        Instant cutoffTime = baseTime.plus(5, ChronoUnit.MINUTES);

        // when
        int affectedRows = jpaRepository.rollupOldSignals(cutoffTime);

        // then
        assertThat(affectedRows).isEqualTo(2);

        // 1번 바구니(0ms 버킷) 검증
        // timestamp_minute은 해당 버킷 내 가장 빠른 시간인 10:00:05 로 기록됨
        Object[] summary1 = (Object[]) em.createNativeQuery(
                "SELECT firepower_avg, firepower_max, offset_ms FROM analysis_signals_summary WHERE timestamp_minute = '2024-01-01 10:00:05+00'")
            .getSingleResult();

        assertThat(((Number) summary1[0]).longValue()).isEqualTo(150L); // 100과 200의 평균
        assertThat(((Number) summary1[1]).longValue()).isEqualTo(200L); // 최대값
        // 5000L이 아니라, 정규화된 바구니의 시작 오프셋인 0L이 들어가야 함!
        assertThat(((Number) summary1[2]).longValue()).isEqualTo(0L);

        // 2번 바구니(60000ms 버킷) 검증
        Object[] summary2 = (Object[]) em.createNativeQuery(
                "SELECT firepower_avg, firepower_max, offset_ms FROM analysis_signals_summary WHERE timestamp_minute = '2024-01-01 10:01:05+00'")
            .getSingleResult();
        assertThat(((Number) summary2[0]).longValue()).isEqualTo(300L);
        assertThat(((Number) summary2[2]).longValue()).isEqualTo(60000L);
    }

    @Test
    void 기준_시간_이전의_원본_데이터만_선택적으로_삭제한다() {
        // given
        Instant now = Instant.now();
        Instant oldTime = now.minus(4, ChronoUnit.DAYS);
        Instant recentTime = now.minus(1, ChronoUnit.DAYS);

        jpaRepository.save(new AnalysisSignalEntity("stream-old", "sessionId","NORMAL", oldTime, 100L, 0L));
        jpaRepository.save(new AnalysisSignalEntity("stream-recent", "sessionId","NORMAL", recentTime, 200L, 86400000L));

        Instant cutoffTime = now.minus(3, ChronoUnit.DAYS);

        // when
        int deletedCount = jpaRepository.deleteOlderThan(cutoffTime);

        // then
        assertThat(deletedCount).isEqualTo(1);

        List<AnalysisSignalEntity> remaining = jpaRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getStreamId()).isEqualTo("stream-recent");
    }

    @Test
    void 특정_세션의_원본_데이터와_요약_데이터를_정확히_조회한다() {
        // given
        String streamId = "test-stream-session";
        String targetSession = "target-session";
        String otherSession = "other-session";
        Instant now = Instant.now();

        // 원본 데이터 세팅 (타겟 세션 2개, 다른 세션 1개)
        jpaRepository.save(new AnalysisSignalEntity(streamId, targetSession, "NORMAL", now, 100L, 0L));
        jpaRepository.save(new AnalysisSignalEntity(streamId, targetSession, "PEAK", now.plusSeconds(3), 500L, 3000L));
        jpaRepository.save(new AnalysisSignalEntity(streamId, otherSession, "NORMAL", now, 50L, 0L));

        // 요약 데이터 생성을 위해 강제 Rollup 실행
        jpaRepository.rollupOldSignals(now.plus(5, ChronoUnit.MINUTES));

        // when 1: 원본 데이터 세션 조회
        List<AnalysisSignalEntity> rawHistory = jpaRepository.findRawHistoryBySession(streamId, targetSession);

        // when 2: 요약 데이터 세션 조회 (Native Query + Projection 검증)
        List<JpaAnalysisSignalRepository.SummaryDataProjection> summaryHistory =
            jpaRepository.findSummaryHistoryBySession(streamId, targetSession);

        // then
        assertThat(rawHistory).hasSize(2); // targetSession 데이터 2개만 나와야 함
        assertThat(rawHistory).extracting("sessionId").containsOnly(targetSession);

        assertThat(summaryHistory).isNotEmpty();
        assertThat(summaryHistory.get(0).getOffsetMs()).isNotNull(); // Projection 매핑이 잘 되었는지 확인
    }
}
