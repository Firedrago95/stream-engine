package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import java.time.Instant;
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
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(StreamRepositoryImpl.class) // 구현체 빈 등록
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamRepositoryImplTest implements PostgresTestSupport {

    @Autowired
    private StreamRepositoryImpl repository;

    @Autowired
    private JpaStreamRepository jpaStreamRepository;

    @Test
    void 임계값_이후에_업데이트된_활성_방송만_시청자순으로_조회한다() {
        // given
        Instant now = Instant.now();

        StreamEntity liveStream1 = new StreamEntity("stream-1", "스트리머A");
        liveStream1.heartbeat("스트리머A", "방송중1", "url", "게임", 100);

        StreamEntity liveStream2 = new StreamEntity("stream-2", "스트리머B");
        liveStream2.heartbeat("스트리머B", "방송중2", "url", "게임", 500); // 시청자 더 많음

        StreamEntity offlineStream = new StreamEntity("stream-3", "스트리머C");
        offlineStream.heartbeat("스트리머C", "종료된방송", "url", "게임", 1000);
        offlineStream.markOffline(); // 오프라인 처리

        jpaStreamRepository.saveAll(List.of(liveStream1, liveStream2, offlineStream));

        // when (임계값을 1시간 전으로 설정하여 최근 업데이트된 내역 모두 포함)
        Instant threshold = now.minus(1, ChronoUnit.HOURS);
        List<StreamEntity> results = repository.findActiveStreams(threshold);

        // then
        assertThat(results).hasSize(2);
        // 시청자 수 내림차순 검증
        assertThat(results.get(0).getStreamerName()).isEqualTo("스트리머B"); // 500명
        assertThat(results.get(1).getStreamerName()).isEqualTo("스트리머A"); // 100명

        // 오프라인 방송은 제외되었는지 검증
        assertThat(results).extracting(StreamEntity::getStreamId)
            .doesNotContain("stream-3");
    }

    @Test
    void 키워드로_스트리머를_검색하면_라이브상태_우선_및_시청자순으로_조회한다() {
        // given
        StreamEntity liveStream = new StreamEntity("ch-1", "침착맨");
        liveStream.heartbeat("침착맨", "라이브 방송", "url", "게임", 100);

        StreamEntity offlineStream = new StreamEntity("ch-2", "침착맨원본박물관");
        offlineStream.heartbeat("침착맨원본박물관", "오프라인 방송", "url", "게임", 1000);
        offlineStream.markOffline(); // 오프라인 (시청자가 많아도 라이브보다 후순위여야 함)

        StreamEntity otherStream = new StreamEntity("ch-3", "주호민");
        otherStream.heartbeat("주호민", "다른 방송", "url", "게임", 500);

        jpaStreamRepository.saveAll(List.of(liveStream, offlineStream, otherStream));

        // when
        List<StreamEntity> results = repository.searchByStreamerName("침착");

        // then
        assertThat(results).hasSize(2);
        // 1순위: 라이브 상태인 '침착맨' (시청자 100)
        assertThat(results.get(0).getStreamerName()).isEqualTo("침착맨");
        // 2순위: 오프라인 상태인 '침착맨원본박물관' (시청자 1000)
        assertThat(results.get(1).getStreamerName()).isEqualTo("침착맨원본박물관");
    }

    @Test
    void 새로운_방송_정보를_upsert하면_DB에_저장된다() {
        // given
        String streamId = "new-stream";
        StreamEntity newEntity = new StreamEntity(streamId, "신규스트리머");
        newEntity.heartbeat("신규스트리머", "첫 방송", "url", "소통", 50);

        // when
        repository.upsertStream(newEntity, Instant.now());

        // then
        Optional<StreamEntity> result = repository.findById(streamId);
        assertThat(result).isPresent();
        assertThat(result.get().getLiveTitle()).isEqualTo("첫 방송");
        assertThat(result.get().getConcurrentUserCount()).isEqualTo(50);
        assertThat(result.get().isLive()).isTrue();
    }

    @Test
    void 기존_방송_정보를_upsert하면_새로운_정보로_업데이트된다() {
        // given
        String streamId = "update-stream";
        Instant initialTime = Instant.now().minus(1, ChronoUnit.HOURS);

        StreamEntity initialEntity = new StreamEntity(streamId, "기존스트리머");
        initialEntity.heartbeat("기존스트리머", "기존 방송", "old-url", "게임", 100);
        repository.upsertStream(initialEntity, initialTime); // 초기 저장

        // when
        StreamEntity updatedEntity = new StreamEntity(streamId, "기존스트리머");
        updatedEntity.heartbeat("기존스트리머", "제목 변경됨", "new-url", "소통", 300);
        repository.upsertStream(updatedEntity, Instant.now()); // 동일한 streamId로 업데이트

        // then
        Optional<StreamEntity> result = repository.findById(streamId);
        assertThat(result).isPresent();
        // 업데이트된 필드들 검증
        assertThat(result.get().getLiveTitle()).isEqualTo("제목 변경됨");
        assertThat(result.get().getProfileImageUrl()).isEqualTo("new-url");
        assertThat(result.get().getCategoryName()).isEqualTo("소통");
        assertThat(result.get().getConcurrentUserCount()).isEqualTo(300);

        // 데이터 개수가 1개로 유지되었는지 검증 (Insert가 아닌 Update 동작)
        assertThat(jpaStreamRepository.count()).isEqualTo(1);
    }

    @Test
    void streamId로_방송_정보를_조회한다() {
        // given
        String streamId = "target-stream";
        StreamEntity entity = new StreamEntity(streamId, "타겟스트리머");
        entity.heartbeat("타겟스트리머", "타겟 방송", "url", "게임", 10);

        jpaStreamRepository.save(entity);

        // when
        Optional<StreamEntity> result = repository.findById(streamId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getStreamId()).isEqualTo(streamId);
        assertThat(result.get().getStreamerName()).isEqualTo("타겟스트리머");
    }
}
