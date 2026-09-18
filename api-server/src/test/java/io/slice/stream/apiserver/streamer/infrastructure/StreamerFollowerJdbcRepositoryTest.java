package io.slice.stream.apiserver.streamer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.streamer.application.dto.StreamFollowerUpdateDto;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerFollowerJdbcRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StreamerFollowerSnapshotRepository snapshotRepository;

    @Autowired
    private JpaStreamRepository streamRepository;

    @Autowired
    private EntityManager em;

    private StreamerFollowerJdbcRepository jdbcRepository;

    @BeforeEach
    void setUp() {
        jdbcRepository = new StreamerFollowerJdbcRepository(jdbcTemplate);
    }

    @Test
    void 스냅샷_목록을_일괄_벌크_업서트하고_충돌_시_최신_수치로_갱신한다() {
        LocalDate date = LocalDate.of(2026, 9, 18);
        StreamerFollowerSnapshotEntity initial = new StreamerFollowerSnapshotEntity("ch1", date, 10000, 100);
        snapshotRepository.save(initial);
        em.flush();
        em.clear();

        StreamerFollowerSnapshotEntity updateCh1 = new StreamerFollowerSnapshotEntity("ch1", date, 10500, 500);
        StreamerFollowerSnapshotEntity newCh2 = new StreamerFollowerSnapshotEntity("ch2", date, 5000, 50);

        jdbcRepository.batchUpsertSnapshots(List.of(updateCh1, newCh2));
        em.clear();

        Optional<StreamerFollowerSnapshotEntity> ch1Result = snapshotRepository.findByStreamIdAndSnapshotDate("ch1", date);
        assertThat(ch1Result).isPresent();
        assertThat(ch1Result.get().getFollowerCount()).isEqualTo(10500);
        assertThat(ch1Result.get().getFollowerGrowth()).isEqualTo(500);

        Optional<StreamerFollowerSnapshotEntity> ch2Result = snapshotRepository.findByStreamIdAndSnapshotDate("ch2", date);
        assertThat(ch2Result).isPresent();
        assertThat(ch2Result.get().getFollowerCount()).isEqualTo(5000);
        assertThat(ch2Result.get().getFollowerGrowth()).isEqualTo(50);
    }

    @Test
    void 스트리머_마스터_테이블의_팔로워_정보를_일괄_벌크_갱신한다() {
        StreamEntity stream1 = new StreamEntity("ch1", "스트리머1");
        StreamEntity stream2 = new StreamEntity("ch2", "스트리머2");
        streamRepository.save(stream1);
        streamRepository.save(stream2);
        em.flush();
        em.clear();

        Instant updateTime = Instant.now();
        List<StreamFollowerUpdateDto> updates = List.of(
            new StreamFollowerUpdateDto("ch1", 25000, updateTime),
            new StreamFollowerUpdateDto("ch2", 35000, updateTime)
        );

        jdbcRepository.batchUpdateStreamFollowers(updates);
        em.clear();

        Optional<StreamEntity> result1 = streamRepository.findByStreamId("ch1");
        assertThat(result1).isPresent();
        assertThat(result1.get().getFollowerCount()).isEqualTo(25000);
        assertThat(result1.get().getLastFollowerUpdatedAt()).isNotNull();

        Optional<StreamEntity> result2 = streamRepository.findByStreamId("ch2");
        assertThat(result2).isPresent();
        assertThat(result2.get().getFollowerCount()).isEqualTo(35000);
        assertThat(result2.get().getLastFollowerUpdatedAt()).isNotNull();
    }
}
