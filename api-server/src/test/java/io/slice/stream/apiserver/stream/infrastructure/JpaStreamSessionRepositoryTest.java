package io.slice.stream.apiserver.stream.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.infrastructure.entity.StreamSessionEntity;
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
            org.springframework.data.domain.PageRequest.of(0, 10)
        );

        // then
        assertThat(recentSessions).hasSize(3);
        // 가장 먼저 나와야 할 데이터는 방금 켠 session-3 (최신순 DESC)
        assertThat(recentSessions.get(0).getSessionId()).isEqualTo("session-3");
        // 가장 마지막에 나와야 할 데이터는 3일 전인 session-1
        assertThat(recentSessions.get(2).getSessionId()).isEqualTo("session-1");
    }
}
