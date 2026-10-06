package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SessionSummaryResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ReLiveSessionMergerTest {

    private ReLiveSessionMerger merger;

    @BeforeEach
    void setUp() {
        merger = new ReLiveSessionMerger();
    }

    @Test
    void gap이_6분_이내인_두_세션은_하나의_리방_그룹으로_병합된다() {
        String streamId = "runner";
        Instant t0 = Instant.parse("2026-10-06T02:00:00Z");
        Instant t1 = t0.plus(Duration.ofMinutes(30)); // 02:30:00
        Instant t2 = t1.plusSeconds(16); // 16초 후 리방 (02:30:16)
        Instant t3 = t2.plus(Duration.ofHours(2)); // 04:30:16

        StreamSessionEntity session1 = new StreamSessionEntity(streamId, "sess-1", "안녕하세여", "종합 게임", t0, false, false);
        session1.finishSession(t1, 1000, 500.0);

        StreamSessionEntity session2 = new StreamSessionEntity(streamId, "sess-2", "안녕하세여2", "종합 게임", t2, false, false);
        session2.finishSession(t3, 2000, 1200.0);

        List<List<StreamSessionEntity>> groups = merger.groupSessions(List.of(session1, session2));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst()).hasSize(2);

        SessionResponse mergedResponse = merger.mergeToSessionResponse(groups.getFirst());
        assertThat(mergedResponse.sessionId()).isEqualTo("sess-2"); // 최신 세션이 마스터
        assertThat(mergedResponse.title()).isEqualTo("안녕하세여2");
        assertThat(mergedResponse.startedAt()).isEqualTo(t0); // 시작 시각은 첫 세션
        assertThat(mergedResponse.endedAt()).isEqualTo(t3); // 종료 시각은 최신 세션
        assertThat(mergedResponse.peakViewers()).isEqualTo(2000); // 피크 시청자 중 최댓값
    }

    @Test
    void gap이_6분을_초과하는_두_세션은_별도의_그룹으로_분리된다() {
        String streamId = "runner";
        Instant t0 = Instant.parse("2026-10-06T02:00:00Z");
        Instant t1 = t0.plus(Duration.ofHours(2));
        Instant t2 = t1.plus(Duration.ofMinutes(10)); // 10분 후 재개 (리방 아님, 2부)
        Instant t3 = t2.plus(Duration.ofHours(2));

        StreamSessionEntity session1 = new StreamSessionEntity(streamId, "sess-1", "1부", "토크", t0, false, false);
        session1.finishSession(t1, 1000, 500.0);

        StreamSessionEntity session2 = new StreamSessionEntity(streamId, "sess-2", "2부", "종합 게임", t2, false, false);
        session2.finishSession(t3, 2000, 1200.0);

        List<List<StreamSessionEntity>> groups = merger.groupSessions(List.of(session1, session2));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0)).containsExactly(session1);
        assertThat(groups.get(1)).containsExactly(session2);
    }

    @Test
    void findLinkedSessionIds는_대상_세션이_속한_모든_리방_세션_ID_목록을_반환한다() {
        String streamId = "runner";
        Instant t0 = Instant.parse("2026-10-06T02:00:00Z");
        Instant t1 = t0.plus(Duration.ofMinutes(20));
        Instant t2 = t1.plusSeconds(30);
        Instant t3 = t2.plus(Duration.ofMinutes(40));

        StreamSessionEntity session1 = new StreamSessionEntity(streamId, "sess-1", "방송1", "게임", t0, false, false);
        session1.finishSession(t1, 500, 300.0);

        StreamSessionEntity session2 = new StreamSessionEntity(streamId, "sess-2", "방송2", "게임", t2, false, false);
        session2.finishSession(t3, 800, 600.0);

        List<StreamSessionEntity> sessions = List.of(session1, session2);

        List<String> linked1 = merger.findLinkedSessionIds("sess-1", sessions);
        List<String> linked2 = merger.findLinkedSessionIds("sess-2", sessions);

        assertThat(linked1).containsExactly("sess-1", "sess-2");
        assertThat(linked2).containsExactly("sess-1", "sess-2");
    }

    @Test
    void 세션_중_하나라도_19금이면_병합된_세션도_19금으로_표시된다() {
        String streamId = "runner";
        Instant t0 = Instant.parse("2026-10-06T02:00:00Z");
        Instant t1 = t0.plus(Duration.ofMinutes(20));
        Instant t2 = t1.plusSeconds(10);

        StreamSessionEntity session1 = new StreamSessionEntity(streamId, "sess-1", "19금전", "게임", t0, false, false);
        session1.finishSession(t1, 500, 300.0);

        StreamSessionEntity session2 = new StreamSessionEntity(streamId, "sess-2", "19금후", "술먹방", t2, false, true);

        SessionSummaryResponse summary = merger.mergeToSessionSummary(List.of(session1, session2));

        assertThat(summary.isAdult()).isTrue();
    }
}
