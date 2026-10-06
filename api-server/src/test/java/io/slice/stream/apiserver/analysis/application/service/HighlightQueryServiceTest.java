package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.stream.fake.FakeHighlightEventRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightQueryServiceTest {

    private FakeHighlightEventRepository highlightRepository;
    private FakeStreamSessionRepository sessionRepository;
    private ReLiveSessionMerger reLiveSessionMerger;
    private HighlightQueryService highlightQueryService;

    @BeforeEach
    void setUp() {
        highlightRepository = new FakeHighlightEventRepository();
        sessionRepository = new FakeStreamSessionRepository();
        reLiveSessionMerger = new ReLiveSessionMerger();
        highlightQueryService = new HighlightQueryService(highlightRepository, sessionRepository, reLiveSessionMerger);
    }

    @Test
    void 특정_과거_세션_ID가_주어지면_해당_세션의_하이라이트를_조회한다() {
        String streamId = "stream-123";
        String sessionId = "past-session";
        Instant start = Instant.parse("2026-03-04T10:00:00Z");

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "과거 방송", "종겜", start);
        session.finishSession(start.plusSeconds(3600), 1000, 800.0);
        sessionRepository.save(session);

        HighlightEventEntity entity = new HighlightEventEntity(
            streamId, sessionId, start, 3600000L, start, 3600000L, 500L
        );
        entity.finish(start.plusSeconds(60), 3660000L);
        highlightRepository.save(entity);

        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, sessionId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).durationSeconds()).isEqualTo(60L);
        assertThat(results.get(0).peakFirepower()).isEqualTo(500L);
    }

    @Test
    void 실시간_조회_시_진행중인_방송이_있으면_현재_세션의_하이라이트를_반환한다() {
        String streamId = "stream-123";
        String activeSessionId = "active-session";
        Instant start = Instant.now();

        StreamSessionEntity activeSession = new StreamSessionEntity(
            streamId, activeSessionId, "방제", "카테고리", start
        );
        sessionRepository.save(activeSession);

        HighlightEventEntity entity = new HighlightEventEntity(
            streamId, activeSessionId, start, 0L, start, 0L, 200L
        );
        highlightRepository.save(entity);

        List<HighlightResponse> resultWithNull = highlightQueryService.getHighlightsBySessionId(streamId, null);
        List<HighlightResponse> resultWithRealtime = highlightQueryService.getHighlightsBySessionId(streamId, "realtime");

        assertThat(resultWithNull).hasSize(1);
        assertThat(resultWithRealtime).hasSize(1);
        assertThat(resultWithNull.get(0).peakFirepower()).isEqualTo(200L);
    }

    @Test
    void 실시간_조회_시_진행중인_방송이_없으면_빈_리스트를_반환한다() {
        String streamId = "stream-123";

        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, "realtime");

        assertThat(results).isEmpty();
    }

    @Test
    void 하이라이트_조회_시_개수_제한_없이_모든_하이라이트를_화력_내림차순으로_반환한다() {
        String streamId = "stream-123";
        String sessionId = "session-123";
        Instant baseTime = Instant.parse("2026-03-04T10:00:00Z");

        StreamSessionEntity session = new StreamSessionEntity(streamId, sessionId, "방제", "카테고리", baseTime);
        sessionRepository.save(session);

        List<HighlightEventEntity> entities = IntStream.rangeClosed(1, 30)
            .mapToObj(i -> {
                HighlightEventEntity entity = new HighlightEventEntity(
                    streamId, sessionId, baseTime.plusSeconds(i * 60L), (long) i * 60000,
                    baseTime.plusSeconds(i * 60L), (long) i * 60000, (long) i * 10
                );
                entity.finish(baseTime.plusSeconds(i * 60L + 30), (long) i * 60000 + 30000);
                return entity;
            })
            .toList();
        highlightRepository.saveAll(entities);

        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, sessionId);

        assertThat(results).hasSize(30);
        assertThat(results.get(0).peakFirepower()).isEqualTo(300L);
        assertThat(results.get(29).peakFirepower()).isEqualTo(10L);
    }

    @Test
    void 리방이_발생한_경우_이전_세션과_신규_세션의_하이라이트가_모두_병합되어_첫_시작_기준_오프셋으로_반환된다() {
        String streamId = "stream-runner";
        Instant t1 = Instant.parse("2026-03-24T11:29:00Z");
        Instant t1End = t1.plusSeconds(60); // 11:30:00 종료
        Instant t2 = t1End.plusSeconds(120); // 11:32:00 (2분 뒤 리방)

        StreamSessionEntity session1 = new StreamSessionEntity(streamId, "sess-1", "1차 방송", "롤", t1);
        session1.finishSession(t1End, 1000, 800.0);
        StreamSessionEntity session2 = new StreamSessionEntity(streamId, "sess-2", "2차 리방", "롤", t2);
        session2.finishSession(t2.plusSeconds(3600), 2500, 2000.0);

        sessionRepository.save(session1);
        sessionRepository.save(session2);

        // sess-1의 하이라이트 (시작 후 30초)
        HighlightEventEntity h1 = new HighlightEventEntity(
            streamId, "sess-1", t1.plusSeconds(30), 30000L, t1.plusSeconds(30), 30000L, 1500L
        );
        h1.finish(t1.plusSeconds(50), 50000L);

        // sess-2의 하이라이트 (sess-2 시작 후 60초 = t1 기준 240초)
        HighlightEventEntity h2 = new HighlightEventEntity(
            streamId, "sess-2", t2.plusSeconds(60), 60000L, t2.plusSeconds(60), 60000L, 3000L
        );
        h2.finish(t2.plusSeconds(90), 90000L);

        highlightRepository.save(h1);
        highlightRepository.save(h2);

        // sess-2로 조회하든 sess-1로 조회하든 병합되어야 함
        List<HighlightResponse> results = highlightQueryService.getHighlightsBySessionId(streamId, "sess-2");

        assertThat(results).hasSize(2);
        // 화력 내림차순 정렬 확인 (3000L이 첫번째)
        assertThat(results.get(0).peakFirepower()).isEqualTo(3000L);
        assertThat(results.get(0).startTimeOffset()).isEqualTo(240000L); // t1 기준 240초

        assertThat(results.get(1).peakFirepower()).isEqualTo(1500L);
        assertThat(results.get(1).startTimeOffset()).isEqualTo(30000L); // t1 기준 30초
    }
}
