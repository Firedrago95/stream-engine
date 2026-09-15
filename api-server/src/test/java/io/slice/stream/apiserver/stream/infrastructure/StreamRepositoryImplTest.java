package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
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
import org.springframework.test.util.ReflectionTestUtils;

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

    @Autowired
    private JpaStreamSessionRepository jpaStreamSessionRepository;

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

        assertThat(results).extracting(StreamEntity::getStreamId)
            .doesNotContain("stream-3");
    }

    @Test
    void 지정된_스트림ID_목록에_해당하는_활성_방송만_시청자순으로_조회한다() {
        Instant now = Instant.now();

        StreamEntity targetLive1 = new StreamEntity("target-1", "스트리머1");
        targetLive1.heartbeat("스트리머1", "방송1", "url", "게임", 300);

        StreamEntity targetLive2 = new StreamEntity("target-2", "스트리머2");
        targetLive2.heartbeat("스트리머2", "방송2", "url", "게임", 800);

        StreamEntity nonTargetLive = new StreamEntity("non-target", "스트리머3");
        nonTargetLive.heartbeat("스트리머3", "방송3", "url", "게임", 1500);

        StreamEntity targetOffline = new StreamEntity("target-offline", "스트리머4");
        targetOffline.heartbeat("스트리머4", "방송4", "url", "게임", 2000);
        targetOffline.markOffline();

        jpaStreamRepository.saveAll(List.of(targetLive1, targetLive2, nonTargetLive, targetOffline));

        Instant threshold = now.minus(1, ChronoUnit.HOURS);
        List<StreamEntity> results = repository.findActiveStreamsByStreamIds(
            List.of("target-1", "target-2", "target-offline"),
            threshold
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStreamId()).isEqualTo("target-2");
        assertThat(results.get(1).getStreamId()).isEqualTo("target-1");
        assertThat(results).extracting(StreamEntity::getStreamId)
            .doesNotContain("non-target", "target-offline");
    }

    @Test
    void streamIds가_빈_목록이거나_null이면_빈_목록을_반환한다() {
        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
        assertThat(repository.findActiveStreamsByStreamIds(List.of(), threshold)).isEmpty();
        assertThat(repository.findActiveStreamsByStreamIds(null, threshold)).isEmpty();
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
        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        List<StreamEntity> results = repository.searchByStreamerName("침착", threshold);

        // then
        assertThat(results).hasSize(2);
        // 1순위: 라이브 상태인 '침착맨' (시청자 100)
        assertThat(results.get(0).getStreamerName()).isEqualTo("침착맨");
        // 2순위: 오프라인 상태인 '침착맨원본박물관' (시청자 1000)
        assertThat(results.get(1).getStreamerName()).isEqualTo("침착맨원본박물관");
    }

    // 💡 [신규 추가] 좀비 스트림 정렬 방어 테스트!
    @Test
    void 좀비_스트림은_라이브_상태여도_검색_시_오프라인으로_간주되어_후순위로_밀린다() {
        // given
        Instant now = Instant.now();
        Instant threshold = now.minus(3, ChronoUnit.MINUTES);

        StreamEntity normalLive = new StreamEntity("ch-1", "정상침착맨");
        normalLive.heartbeat("정상침착맨", "정상방송", "url", "게임", 100);

        StreamEntity zombieLive = new StreamEntity("ch-2", "좀비침착맨");
        zombieLive.heartbeat("좀비침착맨", "좀비방송", "url", "게임", 5000); // 시청자가 훨씬 많음!

        // Reflection을 사용해 강제로 lastUpdateAt을 임계값 이전으로 조작 (좀비 상태 만들기)
        ReflectionTestUtils.setField(zombieLive, "lastUpdateAt", now.minus(10, ChronoUnit.MINUTES));

        jpaStreamRepository.saveAll(List.of(normalLive, zombieLive));

        // when
        List<StreamEntity> results = repository.searchByStreamerName("침착", threshold);

        // then
        assertThat(results).hasSize(2);
        // 좀비 방송이 시청자가 50배 더 많아도, TTL을 지났으므로 CASE WHEN 로직에 의해 후순위로 밀려야 성공!
        assertThat(results.get(0).getStreamerName()).isEqualTo("정상침착맨");
        assertThat(results.get(1).getStreamerName()).isEqualTo("좀비침착맨");
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

    @Test
    void 최근_30일_세션_평균_시청자를_집계하여_스트리머를_키워드로_검색한다() {
        Instant now = Instant.now();
        Instant since = now.minus(30, ChronoUnit.DAYS);

        StreamEntity stream1 = new StreamEntity("s-1", "랄로");
        stream1.heartbeat("랄로", "생방송", "url1", "리그 오브 레전드", 1000);

        StreamEntity stream2 = new StreamEntity("s-2", "랄로팬클럽");
        stream2.heartbeat("랄로팬클럽", "팬방송", "url2", "소통", 300);

        StreamEntity stream3 = new StreamEntity("s-3", "침착맨");
        stream3.heartbeat("침착맨", "침착맨방송", "url3", "토크", 15000);

        jpaStreamRepository.saveAll(List.of(stream1, stream2, stream3));

        StreamSessionEntity session1 = new StreamSessionEntity("s-1", "sess-1", "방송1", "게임", now.minus(5, ChronoUnit.DAYS));
        session1.finishSession(now.minus(4, ChronoUnit.DAYS), 5000, 4000);

        StreamSessionEntity session2 = new StreamSessionEntity("s-1", "sess-2", "방송2", "게임", now.minus(2, ChronoUnit.DAYS));
        session2.finishSession(now.minus(1, ChronoUnit.DAYS), 7000, 6000);

        jpaStreamSessionRepository.saveAll(List.of(session1, session2));

        List<StreamerLeaderboardProjection> results = repository.searchTopStreamersWith30dAvg("랄로", since, 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getStreamerName()).isEqualTo("랄로");
        assertThat(results.get(0).getAverageViewers()).isEqualTo(5000);

        assertThat(results.get(1).getStreamerName()).isEqualTo("랄로팬클럽");
        assertThat(results.get(1).getAverageViewers()).isEqualTo(0);
    }

    @Test
    void 최근_30일간_5회_이상_방송한_정규_스트리머만_리더보드에_조회된다() {
        Instant now = Instant.now();
        Instant since = now.minus(30, ChronoUnit.DAYS);

        StreamEntity regularA = new StreamEntity("reg-a", "정규스트리머A");
        regularA.heartbeat("정규스트리머A", "A방송", "urlA", "종합게임", 5000);

        StreamEntity regularB = new StreamEntity("reg-b", "정규스트리머B");
        regularB.heartbeat("정규스트리머B", "B방송", "urlB", "토크", 8000);

        StreamEntity lowFrequencyC = new StreamEntity("low-c", "활동부족C");
        lowFrequencyC.heartbeat("활동부족C", "가끔방송", "urlC", "소통", 10000);

        StreamEntity pastOnlyD = new StreamEntity("past-d", "과거단발성D");
        pastOnlyD.heartbeat("과거단발성D", "월드컵중계", "urlD", "스포츠", 30000);

        jpaStreamRepository.saveAll(List.of(regularA, regularB, lowFrequencyC, pastOnlyD));

        StreamSessionEntity pastSession = new StreamSessionEntity("past-d", "past-sess", "월드컵중계", "스포츠", now.minus(45, ChronoUnit.DAYS));
        pastSession.finishSession(now.minus(45, ChronoUnit.DAYS).plusSeconds(3600), 35000, 30000);
        jpaStreamSessionRepository.save(pastSession);

        for (int i = 1; i <= 5; i++) {
            StreamSessionEntity s = new StreamSessionEntity("reg-a", "sess-a-" + i, "최근방송A" + i, "게임", now.minus(i * 4, ChronoUnit.DAYS));
            s.finishSession(now.minus(i * 4, ChronoUnit.DAYS).plusSeconds(3600), 6000, 5000);
            jpaStreamSessionRepository.save(s);
        }

        for (int i = 1; i <= 6; i++) {
            StreamSessionEntity s = new StreamSessionEntity("reg-b", "sess-b-" + i, "최근방송B" + i, "토크", now.minus(i * 3, ChronoUnit.DAYS));
            s.finishSession(now.minus(i * 3, ChronoUnit.DAYS).plusSeconds(3600), 9000, 8000);
            jpaStreamSessionRepository.save(s);
        }

        for (int i = 1; i <= 4; i++) {
            StreamSessionEntity s = new StreamSessionEntity("low-c", "sess-c-" + i, "최근방송C" + i, "소통", now.minus(i * 5, ChronoUnit.DAYS));
            s.finishSession(now.minus(i * 5, ChronoUnit.DAYS).plusSeconds(3600), 12000, 10000);
            jpaStreamSessionRepository.save(s);
        }

        List<StreamerLeaderboardProjection> results =
            repository.findTopStreamersWith30dAvg(since, 5, 10);

        assertThat(results).hasSize(2);

        assertThat(results.get(0).getStreamerName()).isEqualTo("정규스트리머B");
        assertThat(results.get(0).getAverageViewers()).isEqualTo(8000);

        assertThat(results.get(1).getStreamerName()).isEqualTo("정규스트리머A");
        assertThat(results.get(1).getAverageViewers()).isEqualTo(5000);

        assertThat(results).extracting(StreamerLeaderboardProjection::getStreamerName)
            .doesNotContain("활동부족C", "과거단발성D");
    }
}
