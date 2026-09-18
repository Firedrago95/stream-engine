package io.slice.stream.apiserver.stream.targeting;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TargetStreamerRepositoryTest implements PostgresTestSupport {

    @Autowired
    private TargetStreamerRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void 활성화된_타겟_스트리머만_조회한다() {
        TargetStreamerEntity active1 = new TargetStreamerEntity("ch-1", "침착맨", TargetType.STATIC, true);
        TargetStreamerEntity active2 = new TargetStreamerEntity("ch-2", "풍월량", TargetType.CUSTOM, true);
        TargetStreamerEntity inactive = new TargetStreamerEntity("ch-3", "비활성스트리머", TargetType.CUSTOM, false);

        repository.saveAll(List.of(active1, active2, inactive));

        List<TargetStreamerEntity> results = repository.findAllByIsActiveTrue();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(TargetStreamerEntity::getChannelId)
            .containsExactlyInAnyOrder("ch-1", "ch-2");
    }

    @Test
    void 채널_ID로_타겟_스트리머를_조회한다() {
        TargetStreamerEntity streamer = new TargetStreamerEntity("target-ch", "옥냥이", TargetType.STATIC, true);
        repository.save(streamer);

        Optional<TargetStreamerEntity> found = repository.findByChannelId("target-ch");
        Optional<TargetStreamerEntity> notFound = repository.findByChannelId("unknown-ch");

        assertThat(found).isPresent();
        assertThat(found.get().getStreamerName()).isEqualTo("옥냥이");
        assertThat(notFound).isEmpty();
    }

    @Test
    void 채널_ID_존재_여부를_확인한다() {
        TargetStreamerEntity streamer = new TargetStreamerEntity("exist-ch", "김도", TargetType.STATIC, true);
        repository.save(streamer);

        boolean exists = repository.existsByChannelId("exist-ch");
        boolean notExists = repository.existsByChannelId("none-ch");

        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }
}
