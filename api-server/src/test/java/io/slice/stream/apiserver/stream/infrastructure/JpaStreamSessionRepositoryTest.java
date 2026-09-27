package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.testcontainer.postgres.PostgresTestSupport;
import jakarta.persistence.EntityManager;
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
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class JpaStreamSessionRepositoryTest implements PostgresTestSupport {

    @Autowired
    private JpaStreamSessionRepository sessionRepository;

    @Autowired
    private EntityManager em;

    @Test
    void 진행중인_세션이_여러_개_열려있을_경우_가장_최근에_시작된_세션_1개만_반환한다() {
        // given
        String streamId = "test-stream-1";
        Instant now = Instant.now();

        // 과거에 열렸던 세션
        StreamSessionEntity oldSession = new StreamSessionEntity(streamId, "session-old", "방제1", "카테고리1", now.minus(2, ChronoUnit.HOURS));
        sessionRepository.save(oldSession);

        // 방금 새로 열린 세션
        StreamSessionEntity newSession = new StreamSessionEntity(streamId, "session-new", "방제2", "카테고리2", now);
        sessionRepository.save(newSession);

        // 이미 닫힌 세션
        StreamSessionEntity closedSession = new StreamSessionEntity(streamId, "session-closed", "방제3", "카테고리3", now.minus(5, ChronoUnit.HOURS));
        closedSession.finishSession(now.minus(4, ChronoUnit.HOURS), 100);
        sessionRepository.save(closedSession);

        // when
        Optional<StreamSessionEntity> activeSession = sessionRepository.findActiveSession(streamId);

        // then
        assertThat(activeSession).isPresent();
        assertThat(activeSession.get().getSessionId()).isEqualTo("session-new"); // 가장 최신 것인지 확인
        assertThat(activeSession.get().getEndedAt()).isNull(); // 열려있는지 확인
    }

    @Test
    void 스트림의_마지막_업데이트_시간이_임계치를_초과한_진행중_세션만_조회한다() {
        // given
        Instant now = Instant.now();
        Instant threshold = now.minus(3, ChronoUnit.MINUTES);

        // 방종 대상
        String stream1 = "zombie-stream";
        em.createNativeQuery("INSERT INTO streams (stream_id, streamer_name, last_update_at) VALUES (?, ?, ?)")
            .setParameter(1, stream1).setParameter(2, "좀비스트리머").setParameter(3, now.minus(4, ChronoUnit.MINUTES))
            .executeUpdate();
        sessionRepository.save(new StreamSessionEntity(stream1, "session-zombie", "방제", "카테고리", now.minus(1, ChronoUnit.HOURS)));

        // 정상 대상
        String stream2 = "active-stream";
        em.createNativeQuery("INSERT INTO streams (stream_id, streamer_name, last_update_at) VALUES (?, ?, ?)")
            .setParameter(1, stream2).setParameter(2, "정상스트리머").setParameter(3, now.minus(1, ChronoUnit.MINUTES))
            .executeUpdate();
        sessionRepository.save(new StreamSessionEntity(stream2, "session-active", "방제", "카테고리", now.minus(1, ChronoUnit.HOURS)));

        // 이미 종료된 세션
        String stream3 = "finished-stream";
        em.createNativeQuery("INSERT INTO streams (stream_id, streamer_name, last_update_at) VALUES (?, ?, ?)")
            .setParameter(1, stream3).setParameter(2, "종료스트리머").setParameter(3, now.minus(10, ChronoUnit.MINUTES))
            .executeUpdate();
        StreamSessionEntity finishedSession = new StreamSessionEntity(stream3, "session-finished", "방제", "카테고리", now.minus(2, ChronoUnit.HOURS));
        finishedSession.finishSession(now.minus(1, ChronoUnit.HOURS), 500);
        sessionRepository.save(finishedSession);

        em.flush();
        em.clear();

        // when
        List<StreamSessionEntity> sessionsToClose = sessionRepository.findSessionsToClose(threshold);

        // then
        assertThat(sessionsToClose).hasSize(1);
        assertThat(sessionsToClose.get(0).getStreamId()).isEqualTo("zombie-stream");
        assertThat(sessionsToClose.get(0).getSessionId()).isEqualTo("session-zombie");
    }

    @Test
    void 스트림의_최근_세션_목록을_최신순으로_가져온다() {
        // given
        String streamId = "test-stream-tabs";
        Instant now = Instant.now();

        // 3일 전, 1일 전, 방금 켠 세션 3개를 순서 섞어서 저장
        sessionRepository.save(new StreamSessionEntity(streamId, "session-1", "방제1", "카테고리1", now.minus(3, ChronoUnit.DAYS)));
        sessionRepository.save(new StreamSessionEntity(streamId, "session-3", "방제3", "카테고리3", now));
        sessionRepository.save(new StreamSessionEntity(streamId, "session-2", "방제2", "카테고리2", now.minus(1, ChronoUnit.DAYS)));

        // when (최대 10개까지만 최신순으로 페이징 조회)
        List<StreamSessionEntity> recentSessions = sessionRepository.findRecentSessionsByStreamId(
            streamId,
            PageRequest.of(0, 10)
        );

        // then
        assertThat(recentSessions).hasSize(3);
        // 가장 먼저 나와야 할 데이터는 방금 켠 session-3 (최신순 DESC)
        assertThat(recentSessions.get(0).getSessionId()).isEqualTo("session-3");
        // 가장 마지막에 나와야 할 데이터는 3일 전인 session-1
        assertThat(recentSessions.get(2).getSessionId()).isEqualTo("session-1");
    }

    @Test
    void 여러_스트림_ID에_대해_현재_열려있는_세션만_일괄_조회한다() {
        // given
        String streamId1 = "stream-a";
        String streamId2 = "stream-b";
        String streamId3 = "stream-c";
        Instant now = Instant.now();

        StreamSessionEntity session1 = new StreamSessionEntity(streamId1, "session-a", "방제1", "카테고리1", now);
        sessionRepository.save(session1);
        StreamSessionEntity session2 = new StreamSessionEntity(streamId2, "session-b", "방제2", "카테고리2", now.minus(2, ChronoUnit.HOURS));
        session2.finishSession(now.minus(1, ChronoUnit.HOURS), 100);
        sessionRepository.save(session2);
        StreamSessionEntity session3 = new StreamSessionEntity(streamId3, "session-c", "방제3", "카테고리3", now);
        sessionRepository.save(session3);

        // when
        List<StreamSessionEntity> activeSessions = sessionRepository.findAllActiveSessions(List.of(streamId1, streamId2, streamId3));

        // then
        assertThat(activeSessions).hasSize(2);
        List<String> activeSessionIds = activeSessions.stream().map(StreamSessionEntity::getSessionId).toList();
        assertThat(activeSessionIds).containsExactlyInAnyOrder("session-a", "session-c");
    }

    @Test
    void 종료된_세션_중_최소_시간_미만인_노이즈_세션은_제외하고_유효한_세션과_라이브_세션만_조회한다() {
        // given
        String streamId = "stream-noise-test";
        Instant now = Instant.now();

        // 1. 92초(1분대) 노이즈 세션 (제외되어야 함)
        StreamSessionEntity noiseSession = new StreamSessionEntity(streamId, "sess-noise", "1분 방송", "Talk", now.minus(5, ChronoUnit.HOURS));
        noiseSession.finishSession(now.minus(5, ChronoUnit.HOURS).plusSeconds(92), 100);
        sessionRepository.save(noiseSession);

        // 2. 정확히 300초(5분) 정상 세션 (포함되어야 함)
        StreamSessionEntity valid5mSession = new StreamSessionEntity(streamId, "sess-5m", "5분 방송", "Game", now.minus(3, ChronoUnit.HOURS));
        valid5mSession.finishSession(now.minus(3, ChronoUnit.HOURS).plusSeconds(300), 200);
        sessionRepository.save(valid5mSession);

        // 3. 2시간 본방송 (포함되어야 함)
        StreamSessionEntity longSession = new StreamSessionEntity(streamId, "sess-long", "본방송", "Game", now.minus(2, ChronoUnit.HOURS));
        longSession.finishSession(now.minus(1, ChronoUnit.HOURS), 1000);
        sessionRepository.save(longSession);

        // 4. 현재 진행중인 라이브 세션 (포함되어야 함)
        StreamSessionEntity liveSession = new StreamSessionEntity(streamId, "sess-live", "라이브 방송", "Talk", now.minusSeconds(30));
        sessionRepository.save(liveSession);

        em.flush();
        em.clear();

        // when
        var pageResult = sessionRepository.findValidSessionsByStreamId(streamId, 300L, PageRequest.of(0, 10));

        // then
        assertThat(pageResult.getTotalElements()).isEqualTo(3);
        List<String> sessionIds = pageResult.getContent().stream().map(StreamSessionEntity::getSessionId).toList();
        assertThat(sessionIds).containsExactly("sess-live", "sess-long", "sess-5m");
        assertThat(sessionIds).doesNotContain("sess-noise");
    }

    @Test
    void 유료_프로모션_조회_시_광고_방송이면서_최소_시간_이상인_세션만_조회한다() {
        // given
        String streamId = "stream-paid-test";
        Instant now = Instant.now();

        // 1. 광고 방송이지만 1분 노이즈 세션 (제외)
        StreamSessionEntity paidNoise = new StreamSessionEntity(streamId, "sess-paid-noise", "광고 1분", "Game", now.minus(4, ChronoUnit.HOURS), true);
        paidNoise.finishSession(now.minus(4, ChronoUnit.HOURS).plusSeconds(60), 50);
        sessionRepository.save(paidNoise);

        // 2. 일반 방송 2시간 (광고 아니므로 제외)
        StreamSessionEntity normalLong = new StreamSessionEntity(streamId, "sess-normal", "일반 본방", "Talk", now.minus(3, ChronoUnit.HOURS), false);
        normalLong.finishSession(now.minus(1, ChronoUnit.HOURS), 1000);
        sessionRepository.save(normalLong);

        // 3. 광고 방송 1시간 (포함)
        StreamSessionEntity paidValid = new StreamSessionEntity(streamId, "sess-paid-valid", "광고 본방", "Game", now.minus(1, ChronoUnit.HOURS), true);
        paidValid.finishSession(now, 500);
        sessionRepository.save(paidValid);

        em.flush();
        em.clear();

        // when
        var pageResult = sessionRepository.findValidSessionsByStreamIdAndPaidPromotionTrue(streamId, 300L, PageRequest.of(0, 10));

        // then
        assertThat(pageResult.getTotalElements()).isEqualTo(1);
        assertThat(pageResult.getContent().get(0).getSessionId()).isEqualTo("sess-paid-valid");
    }
}
