package io.slice.stream.apiserver.streamer.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
class JpaStreamerSimilarityRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaStreamerSimilarityRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void 스트리머별_최신_일자의_유사도_데이터를_순위순으로_조회한다() {
        String streamId = "streamer1";
        LocalDate yesterday = LocalDate.of(2026, 9, 27);
        LocalDate today = LocalDate.of(2026, 9, 28);

        // 어제 데이터
        repository.save(new StreamerSimilarityEntity(
            streamId, SimilarityStatus.NORMAL, "oldStreamer", "과거스트리머", "url", "롤", 1, 20.0, 100, 500, yesterday
        ));

        // 오늘 데이터 (1위, 2위)
        repository.save(new StreamerSimilarityEntity(
            streamId, SimilarityStatus.NORMAL, "streamerB", "스트리머B", "urlB", "발로란트", 1, 35.0, 200, 600, today
        ));
        repository.save(new StreamerSimilarityEntity(
            streamId, SimilarityStatus.NORMAL, "streamerC", "스트리머C", "urlC", "종합게임", 2, 25.0, 150, 650, today
        ));

        List<StreamerSimilarityEntity> result = repository.findLatestSimilaritiesByStreamId(streamId);

        assertAll(
            () -> assertThat(result).hasSize(2),
            () -> assertThat(result.get(0).getCalculatedDate()).isEqualTo(today),
            () -> assertThat(result.get(0).getRankOrder()).isEqualTo(1),
            () -> assertThat(result.get(0).getTargetStreamerName()).isEqualTo("스트리머B"),
            () -> assertThat(result.get(1).getRankOrder()).isEqualTo(2),
            () -> assertThat(result.get(1).getTargetStreamerName()).isEqualTo("스트리머C")
        );
    }

    @Test
    void 동일_일자_데이터_삭제_시_해당_일자_레코드만_삭제된다() {
        String streamId = "streamer2";
        LocalDate day1 = LocalDate.of(2026, 9, 27);
        LocalDate day2 = LocalDate.of(2026, 9, 28);

        repository.save(new StreamerSimilarityEntity(
            streamId, SimilarityStatus.NORMAL, "s1", "이름1", "url", "롤", 1, 30.0, 100, 400, day1
        ));
        repository.save(new StreamerSimilarityEntity(
            streamId, SimilarityStatus.NORMAL, "s2", "이름2", "url", "롤", 1, 30.0, 100, 400, day2
        ));

        repository.deleteByCalculatedDate(day2);

        List<StreamerSimilarityEntity> remaining = repository.findAll();
        assertAll(
            () -> assertThat(remaining).hasSize(1),
            () -> assertThat(remaining.getFirst().getCalculatedDate()).isEqualTo(day1)
        );
    }
}
