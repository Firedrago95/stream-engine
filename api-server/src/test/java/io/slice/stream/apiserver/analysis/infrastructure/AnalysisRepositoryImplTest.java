package io.slice.stream.apiserver.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(AnalysisRepositoryImpl.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisRepositoryImplTest implements PostgresTestSupport {

    @Autowired
    private AnalysisRepositoryImpl analysisRepository;

    @Autowired
    private JpaAnalysisSignalRepository jpaRepository;

    @Test
    void 도메인_모델을_저장하면_실제_DB_엔티티로_변환되어_저장된다() {
        // given
        Instant now = Instant.now();
        AnalysisSignal domainSignal = AnalysisSignal.of("stream-A", "NORMAL", now, 100L);

        // when
        analysisRepository.save(domainSignal);

        // then
        List<AnalysisSignalEntity> entities = jpaRepository.findAll();
        assertThat(entities).hasSize(1);
        AnalysisSignalEntity saved = entities.get(0);
        assertThat(saved.getStreamId()).isEqualTo("stream-A");
        assertThat(saved.getFirepower()).isEqualTo(100L);
    }

    @Test
    void 특정_스트림의_최신_데이터를_도메인_객체_리스트로_반환한다() {
        // given
        String streamId = "stream-1";
        Instant now = Instant.now();

        // 직접 엔티티 저장 (데이터 준비)
        analysisRepository.save(AnalysisSignal.of(streamId, "NORMAL", now.minusSeconds(10), 50L));
        analysisRepository.save(AnalysisSignal.of(streamId, "PEAK", now, 200L));

        // when
        List<AnalysisSignal> results = analysisRepository.findRecentSignals(streamId, 1);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("PEAK");
        assertThat(results.get(0).firepower()).isEqualTo(200L);
    }

    @Test
    void 여러_스트림_ID_중_실제_데이터가_있는_ID만_추출한다() {
        // given
        analysisRepository.save(AnalysisSignal.of("ch1", "NORMAL", Instant.now(), 10L));
        analysisRepository.save(AnalysisSignal.of("ch2", "NORMAL", Instant.now(), 20L));

        List<String> requestIds = List.of("ch1", "ch2", "ch3");

        // when
        Set<String> activeChannels = analysisRepository.findChannelsWithRecentSignals(requestIds);

        // then
        assertThat(activeChannels).hasSize(2)
            .containsExactlyInAnyOrder("ch1", "ch2")
            .doesNotContain("ch3");
    }

    @Test
    void 요청한_ID_목록이_비어있으면_빈_결과를_반환한다() {
        // when
        Set<String> activeChannels = analysisRepository.findChannelsWithRecentSignals(List.of());

        // then
        assertThat(activeChannels).isEmpty();
    }

    @Test
    void 특정_날짜_범위의_원본_데이터를_조회하여_DTO로_반환한다() {
        // given
        String streamId = "stream-history";
        Instant today = Instant.now();
        Instant yesterday = today.minus(1, ChronoUnit.DAYS);

        // 데이터 준비: 어제 1건, 오늘 1건
        analysisRepository.save(AnalysisSignal.of(streamId, "NORMAL", yesterday, 50L));
        analysisRepository.save(AnalysisSignal.of(streamId, "PEAK", today, 200L));

        Instant start = today.minus(1, ChronoUnit.HOURS);
        Instant end = today.plus(1, ChronoUnit.HOURS);

        // when (오늘 범위만 조회)
        List<AnalysisDataPoint> history = analysisRepository.findRawHistory(streamId, start, end);

        // then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).status()).isEqualTo("PEAK");
        assertThat(history.get(0).value()).isEqualTo(200L);
    }

    // 💡 [추가 2] 가용 날짜 목록 조회 쿼리 정상 실행 검증
    @Test
    void 커서를_이용한_가용_날짜_목록을_조회한다() {
        // given
        String streamId = "stream-dates";
        analysisRepository.save(AnalysisSignal.of(streamId, "NORMAL", Instant.now(), 50L));

        LocalDate cursor = LocalDate.MAX;

        // when
        List<LocalDate> dates = analysisRepository.findAvailableDates(streamId, cursor, 10);

        // then
        assertThat(dates).isNotEmpty();
        // UTC와 KST 차이가 있을 수 있으나, 정상적으로 날짜가 뽑히는지 검증
    }
}
