package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import io.slice.stream.apiserver.stream.fake.FakeStreamRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionRepository;
import io.slice.stream.apiserver.stream.fake.FakeStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.fake.FakeViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSessionSummaryRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionServiceTest {

    private FakeStreamSessionRepository sessionRepository;
    private FakeStreamRepository streamRepository;
    private FakeStreamSessionSegmentRepository segmentRepository;
    private FakeViewMetricTimelineRepository timelineRepository;
    private ConcurrentMapCacheManager cacheManager;
    private MeterRegistry meterRegistry;
    private StreamSessionService streamSessionService;

    @BeforeEach
    void setUp() {
        sessionRepository = new FakeStreamSessionRepository();
        streamRepository = new FakeStreamRepository();
        segmentRepository = new FakeStreamSessionSegmentRepository();
        timelineRepository = new FakeViewMetricTimelineRepository();
        cacheManager = new ConcurrentMapCacheManager("activeSessions");
        meterRegistry = new SimpleMeterRegistry();

        streamSessionService = new StreamSessionService(
            sessionRepository,
            streamRepository,
            segmentRepository,
            timelineRepository,
            cacheManager,
            meterRegistry
        );
    }

    @Test
    void 오프라인_임계치를_초과한_방종_세션을_찾아_종료하고_캐시를_명시적으로_제거한다() {
        String streamId = "stream-3";
        StreamSessionEntity zombieSession = new StreamSessionEntity(
            streamId, "zombie-session-id", "방제", "카테고리", Instant.now().minusSeconds(3600)
        );
        sessionRepository.addSession(zombieSession);
        sessionRepository.setSessionsToClose(List.of(zombieSession));
        timelineRepository.setAverageViewerCount("zombie-session-id", 150.0);
        timelineRepository.setPeakViewerCount("zombie-session-id", 300);

        Cache cache = cacheManager.getCache("activeSessions");
        assertThat(cache).isNotNull();
        cache.put(streamId, "cachedSession");

        streamSessionService.closeOfflineSessions();

        assertThat(zombieSession.getEndedAt()).isNotNull();
        assertThat(zombieSession.getPeakViewers()).isEqualTo(300);
        assertThat(zombieSession.getAverageViewerCount()).isEqualTo(150);
        assertThat(cache.get(streamId)).isNull();
    }

    @Test
    void 오프라인_세션_종료시_스트림의_마지막_갱신시각으로_세션과_세그먼트가_마감된다() {
        String streamId = "stream-real-close";
        String sessionId = "session-real-close";
        Instant streamStartedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant streamLastUpdatedAt = Instant.parse("2026-02-13T11:00:00Z");

        StreamSessionEntity zombieSession = new StreamSessionEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt, 0L);
        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머", streamLastUpdatedAt);

        sessionRepository.addSession(zombieSession);
        sessionRepository.setSessionsToClose(List.of(zombieSession));
        segmentRepository.addSegment(activeSegment);
        streamRepository.addStream(streamEntity);
        timelineRepository.setAverageViewerCount(sessionId, 100.0);
        timelineRepository.setPeakViewerCount(sessionId, 200);

        streamSessionService.closeOfflineSessions();

        assertThat(zombieSession.getEndedAt()).isEqualTo(streamLastUpdatedAt);
        assertThat(activeSegment.getEndedAt()).isEqualTo(streamLastUpdatedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(3600000L);
    }

    @Test
    void 오프라인_세션_종료시_스트림_마지막_갱신시각이_시작시각보다_과거이면_시작시각으로_역전방어된다() {
        String streamId = "stream-inversion";
        String sessionId = "session-inversion";
        Instant streamStartedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant streamLastUpdatedAt = Instant.parse("2026-02-13T09:30:00Z");

        StreamSessionEntity zombieSession = new StreamSessionEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt, 0L);
        StreamEntity streamEntity = new StreamEntity(streamId, "스트리머", streamLastUpdatedAt);

        sessionRepository.addSession(zombieSession);
        sessionRepository.setSessionsToClose(List.of(zombieSession));
        segmentRepository.addSegment(activeSegment);
        streamRepository.addStream(streamEntity);
        timelineRepository.setAverageViewerCount(sessionId, 0.0);
        timelineRepository.setPeakViewerCount(sessionId, 0);

        streamSessionService.closeOfflineSessions();

        assertThat(zombieSession.getEndedAt()).isEqualTo(streamStartedAt);
        assertThat(activeSegment.getEndedAt()).isEqualTo(streamStartedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(0L);
    }

    @Test
    void 방제나_카테고리가_변경되면_기존_세그먼트를_종료하고_새로운_세그먼트를_저장한다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant changedAt = Instant.now();
        Long offsetMs = 1000L;

        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, sessionId, "이전방제", "이전카테고리", Instant.now().minusSeconds(60));
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "이전방제", "이전카테고리", Instant.now().minusSeconds(60), 0L);
        ChangedStreamRequest request = new ChangedStreamRequest(streamId, sessionId, "이전방제", "새로운방제", "이전카테고리", "새로운카테고리", changedAt, offsetMs);

        sessionRepository.addSession(activeSession);
        segmentRepository.addSegment(activeSegment);

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isEqualTo(changedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(offsetMs);
        assertThat(activeSession.getTitle()).isEqualTo("새로운방제");
        assertThat(activeSession.getCategoryName()).isEqualTo("새로운카테고리");

        Optional<StreamSessionSegmentEntity> activeSegOpt = segmentRepository.findActiveSegment(sessionId);
        assertThat(activeSegOpt).isPresent();
        assertThat(activeSegOpt.get().getTitle()).isEqualTo("새로운방제");
        assertThat(activeSegOpt.get().getCategoryName()).isEqualTo("새로운카테고리");
        assertThat(activeSegOpt.get().getStartOffsetMs()).isEqualTo(offsetMs);
    }

    @Test
    void 세그먼트_갱신_시_paidPromotion이_참이면_세션과_새_세그먼트에_유료_프로모션이_반영된다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant changedAt = Instant.now();
        Long offsetMs = 1000L;

        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, sessionId, "이전방제", "이전카테고리", Instant.now().minusSeconds(60), false);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "이전방제", "이전카테고리", Instant.now().minusSeconds(60), 0L, false);
        ChangedStreamRequest request = new ChangedStreamRequest(streamId, sessionId, "이전방제", "숙제방송", "이전카테고리", "게임", changedAt, offsetMs, true);

        sessionRepository.addSession(activeSession);
        segmentRepository.addSegment(activeSegment);

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSession.isPaidPromotion()).isTrue();
        Optional<StreamSessionSegmentEntity> activeSegOpt = segmentRepository.findActiveSegment(sessionId);
        assertThat(activeSegOpt).isPresent();
        assertThat(activeSegOpt.get().isPaidPromotion()).isTrue();
    }

    @Test
    void 변경된_방제나_카테고리가_기존과_완전히_동일하면_세그먼트를_갱신하지_않는다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant changedAt = Instant.now();
        Long offsetMs = 1000L;

        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, sessionId, "동일방제", "동일카테고리", Instant.now().minusSeconds(60));
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "동일방제", "동일카테고리", Instant.now().minusSeconds(60), 0L);
        ChangedStreamRequest request = new ChangedStreamRequest(streamId, sessionId, "동일방제", "동일방제", "동일카테고리", "동일카테고리", changedAt, offsetMs);

        sessionRepository.addSession(activeSession);
        segmentRepository.addSegment(activeSegment);

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isNull();
        assertThat(activeSegment.getEndOffsetMs()).isNull();
        assertThat(segmentRepository.getAllSegments()).hasSize(1);
    }

    @Test
    void 방제_변경_직후_10초_이내에_유료프로모션_보정_요청이_오면_기존_세그먼트의_광고상태가_보정된다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant startedAt = Instant.now().minusSeconds(3);
        Instant changedAt = Instant.now();
        Long offsetMs = 3000L;

        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, sessionId, "동일방제", "동일카테고리", startedAt, false);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "동일방제", "동일카테고리", startedAt, 0L, false);
        ChangedStreamRequest request = new ChangedStreamRequest(streamId, sessionId, "동일방제", "동일방제", "동일카테고리", "동일카테고리", changedAt, offsetMs, true);

        sessionRepository.addSession(activeSession);
        segmentRepository.addSegment(activeSegment);

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.isPaidPromotion()).isTrue();
        assertThat(activeSegment.getEndedAt()).isNull();
        assertThat(activeSession.isPaidPromotion()).isTrue();
        assertThat(segmentRepository.getAllSegments()).hasSize(1);
    }

    @Test
    void 방제와_카테고리는_동일하지만_10초_초과_후_유료프로모션_상태만_바뀌면_새_세그먼트가_분리된다() {
        String streamId = "stream-1";
        String sessionId = "session-1";
        Instant startedAt = Instant.now().minusSeconds(60);
        Instant changedAt = Instant.now();
        Long offsetMs = 60000L;

        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, sessionId, "동일방제", "동일카테고리", startedAt, false);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "동일방제", "동일카테고리", startedAt, 0L, false);
        ChangedStreamRequest request = new ChangedStreamRequest(streamId, sessionId, "동일방제", "동일방제", "동일카테고리", "동일카테고리", changedAt, offsetMs, true);

        sessionRepository.addSession(activeSession);
        segmentRepository.addSegment(activeSegment);

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isEqualTo(changedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(offsetMs);

        Optional<StreamSessionSegmentEntity> activeSegOpt = segmentRepository.findActiveSegment(sessionId);
        assertThat(activeSegOpt).isPresent();
        assertThat(activeSegOpt.get()).isNotSameAs(activeSegment);
        assertThat(activeSegOpt.get().isPaidPromotion()).isTrue();
    }

    @Test
    void 방송_세션_요약정보가_수신되면_정상적으로_구독자_비율과_피크_평균시청자가_업데이트되고_활성_세그먼트도_마감된다() {
        String streamId = "stream-summary";
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant endedAt = Instant.parse("2026-02-13T12:00:00Z");
        StreamSessionEntity session = new StreamSessionEntity(streamId, "test-live-id", "방제", "카테고리", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, "test-live-id", "방제", "카테고리", startedAt, 0L);

        sessionRepository.addSession(session);
        segmentRepository.addSegment(activeSegment);
        timelineRepository.setAverageViewerCount("test-live-id", 520.4);
        timelineRepository.setPeakViewerCount("test-live-id", 850);

        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(45.5, "test-live-id", endedAt);

        streamSessionService.updateSessionSummary(streamId, request);

        assertThat(session.getSubscriberChatRatio()).isEqualTo(45.5);
        assertThat(session.getPeakViewers()).isEqualTo(850);
        assertThat(session.getAverageViewerCount()).isEqualTo(520);
        assertThat(session.getEndedAt()).isEqualTo(endedAt);
        assertThat(activeSegment.getEndedAt()).isEqualTo(endedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(7200000L);
    }

    @Test
    void 방송_세션_요약정보_수신시_활성세션이_없어도_예외_없이_정상_종료된다() {
        String streamId = "stream-not-found";
        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(45.5, "test-live-id", Instant.now());

        assertDoesNotThrow(
            () -> streamSessionService.updateSessionSummary(streamId, request)
        );
    }

    @Test
    void 방종시각이_null로_수신되면_현재_서버시각으로_안전하게_보정되어_세션과_세그먼트가_마감된다() {
        String streamId = "stream-null-ended";
        Instant startedAt = Instant.now().minusSeconds(3600);
        StreamSessionEntity session = new StreamSessionEntity(streamId, "null-ended-id", "방제", "카테고리", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, "null-ended-id", "방제", "카테고리", startedAt, 0L);

        sessionRepository.addSession(session);
        segmentRepository.addSegment(activeSegment);
        timelineRepository.setAverageViewerCount("null-ended-id", 100.0);
        timelineRepository.setPeakViewerCount("null-ended-id", 200);

        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(30.0, "null-ended-id", null);

        assertDoesNotThrow(() -> streamSessionService.updateSessionSummary(streamId, request));

        assertThat(session.getEndedAt()).isNotNull();
        assertThat(session.getEndedAt()).isAfterOrEqualTo(startedAt);
        assertThat(activeSegment.getEndedAt()).isNotNull();
        assertThat(activeSegment.getEndOffsetMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void 방종시각이_세션_시작시각보다_과거로_수신되면_서버_현재시각으로_보정되어_음수_오프셋이_발생하지_않는다() {
        String streamId = "stream-invalid-ended";
        Instant startedAt = Instant.now().minusSeconds(1800);
        Instant pastEndedAt = startedAt.minusSeconds(600);
        StreamSessionEntity session = new StreamSessionEntity(streamId, "invalid-ended-id", "방제", "카테고리", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, "invalid-ended-id", "방제", "카테고리", startedAt, 0L);

        sessionRepository.addSession(session);
        segmentRepository.addSegment(activeSegment);
        timelineRepository.setAverageViewerCount("invalid-ended-id", 100.0);
        timelineRepository.setPeakViewerCount("invalid-ended-id", 200);

        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(30.0, "invalid-ended-id", pastEndedAt);

        assertDoesNotThrow(() -> streamSessionService.updateSessionSummary(streamId, request));

        assertThat(session.getEndedAt()).isAfterOrEqualTo(startedAt);
        assertThat(activeSegment.getEndedAt()).isAfterOrEqualTo(startedAt);
        assertThat(activeSegment.getEndOffsetMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void 이미_종료된_세션에_요약정보가_수신되어도_정밀_시각으로_보정되고_일별_통계가_재적재된다() {
        String streamId = "stream-summary-reconcile";
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant oldEndedAt = Instant.parse("2026-02-13T11:55:00Z");
        Instant accurateEndedAt = Instant.parse("2026-02-13T12:00:00Z");

        StreamSessionEntity session = new StreamSessionEntity(streamId, "test-live-id", "방제", "카테고리", startedAt);
        session.finishSession(oldEndedAt, 800, 500);

        sessionRepository.addSession(session);
        timelineRepository.setAverageViewerCount("test-live-id", 520.4);
        timelineRepository.setPeakViewerCount("test-live-id", 850);

        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(50.0, "test-live-id", accurateEndedAt);

        streamSessionService.updateSessionSummary(streamId, request);

        assertThat(session.getSubscriberChatRatio()).isEqualTo(50.0);
        assertThat(session.getEndedAt()).isEqualTo(accurateEndedAt);
        assertThat(session.getPeakViewers()).isEqualTo(850);
        assertThat(session.getAverageViewerCount()).isEqualTo(520);
    }

    @Test
    void 방송중_19금_연령제한이_설정되면_기존_세그먼트가_마감되고_19금_신규_세그먼트가_생성된다() {
        String streamId = "stream-adult-test";
        String liveId = "live-adult-123";
        Instant startedAt = Instant.parse("2026-10-06T10:00:00Z");
        Instant adultChangedAt = Instant.parse("2026-10-06T10:30:00Z");

        StreamSessionEntity session = new StreamSessionEntity(streamId, liveId, "일반 토크", "talk", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(
            streamId, liveId, "일반 토크", "talk", startedAt, 0L, false, false
        );

        sessionRepository.addSession(session);
        segmentRepository.addSegment(activeSegment);

        ChangedStreamRequest request = new ChangedStreamRequest(
            streamId, liveId, "일반 토크", "일반 토크", "talk", "talk", adultChangedAt, 1800000L, false, true
        );

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isEqualTo(adultChangedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(1800000L);
        assertThat(session.isAdult()).isTrue();

        Optional<StreamSessionSegmentEntity> newActiveSegOpt = segmentRepository.findActiveSegment(liveId);
        assertThat(newActiveSegOpt).isPresent();
        assertThat(newActiveSegOpt.get().isAdult()).isTrue();
        assertThat(newActiveSegOpt.get().getStartOffsetMs()).isEqualTo(1800000L);
    }
}
