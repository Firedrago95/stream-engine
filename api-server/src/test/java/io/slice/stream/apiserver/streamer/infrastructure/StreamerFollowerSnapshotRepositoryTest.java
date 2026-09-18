package io.slice.stream.apiserver.streamer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
class StreamerFollowerSnapshotRepositoryTest implements PostgresTestSupport {

    @Autowired
    private StreamerFollowerSnapshotRepository snapshotRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 특정_스트림의_특정_날짜_스냅샷을_조회한다() {
        String streamId = "stream-1";
        LocalDate targetDate = LocalDate.of(2026, 3, 15);

        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, targetDate, 15000, 200));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, targetDate.minusDays(1), 14800, 100));

        Optional<StreamerFollowerSnapshotEntity> result = snapshotRepository.findByStreamIdAndSnapshotDate(streamId, targetDate);

        assertThat(result).isPresent();
        assertThat(result.get().getFollowerCount()).isEqualTo(15000);
        assertThat(result.get().getFollowerGrowth()).isEqualTo(200);
    }

    @Test
    void 특정_날짜_이후의_스냅샷_목록을_날짜_오름차순으로_조회한다() {
        String streamId = "stream-history";
        LocalDate day1 = LocalDate.of(2026, 3, 10);
        LocalDate day2 = LocalDate.of(2026, 3, 11);
        LocalDate day3 = LocalDate.of(2026, 3, 12);
        LocalDate oldDay = LocalDate.of(2026, 3, 5);

        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, day3, 1300, 30));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, day1, 1100, 10));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, oldDay, 900, 5));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, day2, 1200, 20));

        List<StreamerFollowerSnapshotEntity> results =
            snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(streamId, day1);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getSnapshotDate()).isEqualTo(day1);
        assertThat(results.get(1).getSnapshotDate()).isEqualTo(day2);
        assertThat(results.get(2).getSnapshotDate()).isEqualTo(day3);
    }

    @Test
    void 특정_날짜_이전의_가장_최신_스냅샷_1건을_조회한다() {
        String streamId = "stream-baseline";
        LocalDate baseDate = LocalDate.of(2026, 3, 10);

        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, baseDate.minusDays(5), 1000, 10));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, baseDate.minusDays(1), 1200, 20));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, baseDate.plusDays(1), 1300, 30));

        Optional<StreamerFollowerSnapshotEntity> result =
            snapshotRepository.findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(streamId, baseDate);

        assertThat(result).isPresent();
        assertThat(result.get().getSnapshotDate()).isEqualTo(baseDate.minusDays(1));
        assertThat(result.get().getFollowerCount()).isEqualTo(1200);
    }

    @Test
    void 기준일_이전의_만료된_스냅샷을_일괄_삭제하고_기준일_이후_데이터는_보존한다() {
        String streamId = "stream-cleanup";
        LocalDate cutoffDate = LocalDate.of(2026, 3, 1);

        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, cutoffDate.minusDays(10), 100, 0));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, cutoffDate.minusDays(1), 150, 5));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, cutoffDate, 200, 10));
        snapshotRepository.save(new StreamerFollowerSnapshotEntity(streamId, cutoffDate.plusDays(5), 250, 15));

        int deletedCount = snapshotRepository.deleteExpiredSnapshots(cutoffDate);
        em.flush();
        em.clear();

        assertThat(deletedCount).isEqualTo(2);

        List<StreamerFollowerSnapshotEntity> remaining = snapshotRepository.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(StreamerFollowerSnapshotEntity::getSnapshotDate)
            .containsExactlyInAnyOrder(cutoffDate, cutoffDate.plusDays(5));
    }
}
