package io.slice.stream.apiserver.stream.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSessionSummaryRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionServiceTest {

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private JpaStreamSessionSegmentRepository segmentRepository;

    @Mock
    private JpaViewMetricTimelineRepository timelineRepository;

    @Mock
    private CacheManager cacheManager;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private StreamSessionService streamSessionService;




    @Test
    void 오프라인_임계치를_초과한_방종_세션을_찾아_종료하고_캐시를_명시적으로_제거한다() {
        String streamId = "stream-3";
        StreamSessionEntity zombieSession = new StreamSessionEntity(streamId, "zombie-session-id", "방제", "카테고리", Instant.now().minusSeconds(3600));

        when(sessionRepository.findSessionsToClose(any(Instant.class)))
            .thenReturn(List.of(zombieSession));
        when(timelineRepository.findAverageViewerCountBySessionId("zombie-session-id"))
            .thenReturn(150.0);
        when(timelineRepository.findPeakViewerCountBySessionId("zombie-session-id"))
            .thenReturn(300);
        when(streamRepository.findAllByStreamIdIn(List.of(streamId)))
            .thenReturn(List.of());

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("activeSessions")).thenReturn(mockCache);

        streamSessionService.closeOfflineSessions();

        assertThat(zombieSession.getEndedAt()).isNotNull();
        assertThat(zombieSession.getPeakViewers()).isEqualTo(300);
        assertThat(zombieSession.getAverageViewerCount()).isEqualTo(150);
        verify(mockCache, times(1)).evict(streamId);
    }

    @Test
    void 오프라인_세션_종료시_스트림의_마지막_갱신시각으로_세션과_세그먼트가_마감된다() {
        String streamId = "stream-real-close";
        String sessionId = "session-real-close";
        Instant streamStartedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant streamLastUpdatedAt = Instant.parse("2026-02-13T11:00:00Z");

        StreamSessionEntity zombieSession = new StreamSessionEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, sessionId, "방제", "카테고리", streamStartedAt, 0L);

        when(sessionRepository.findSessionsToClose(any(Instant.class)))
            .thenReturn(List.of(zombieSession));
        when(timelineRepository.findAverageViewerCountBySessionId(sessionId))
            .thenReturn(100.0);
        when(timelineRepository.findPeakViewerCountBySessionId(sessionId))
            .thenReturn(200);

        StreamEntity streamEntity = mock(StreamEntity.class);
        when(streamEntity.getStreamId()).thenReturn(streamId);
        when(streamEntity.getLastUpdateAt()).thenReturn(streamLastUpdatedAt);
        when(streamRepository.findAllByStreamIdIn(List.of(streamId)))
            .thenReturn(List.of(streamEntity));

        when(segmentRepository.findActiveSegment(sessionId))
            .thenReturn(Optional.of(activeSegment));

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("activeSessions")).thenReturn(mockCache);

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

        when(sessionRepository.findSessionsToClose(any(Instant.class)))
            .thenReturn(List.of(zombieSession));
        when(timelineRepository.findAverageViewerCountBySessionId(sessionId))
            .thenReturn(0.0);
        when(timelineRepository.findPeakViewerCountBySessionId(sessionId))
            .thenReturn(0);

        StreamEntity streamEntity = mock(StreamEntity.class);
        when(streamEntity.getStreamId()).thenReturn(streamId);
        when(streamEntity.getLastUpdateAt()).thenReturn(streamLastUpdatedAt);
        when(streamRepository.findAllByStreamIdIn(List.of(streamId)))
            .thenReturn(List.of(streamEntity));

        when(segmentRepository.findActiveSegment(sessionId))
            .thenReturn(Optional.of(activeSegment));

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("activeSessions")).thenReturn(mockCache);

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

        when(sessionRepository.findAllActiveSessions(List.of(streamId)))
            .thenReturn(List.of(activeSession));
        when(segmentRepository.findAllActiveSegments(List.of(sessionId)))
            .thenReturn(List.of(activeSegment));

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isEqualTo(changedAt);
        assertThat(activeSegment.getEndOffsetMs()).isEqualTo(offsetMs);
        assertThat(activeSession.getTitle()).isEqualTo("새로운방제");
        assertThat(activeSession.getCategoryName()).isEqualTo("새로운카테고리");
        verify(segmentRepository, times(1)).saveAll(any());
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

        when(sessionRepository.findAllActiveSessions(List.of(streamId)))
            .thenReturn(List.of(activeSession));
        when(segmentRepository.findAllActiveSegments(List.of(sessionId)))
            .thenReturn(List.of(activeSegment));

        streamSessionService.updateSessionSegment(List.of(request));

        assertThat(activeSegment.getEndedAt()).isNull();
        assertThat(activeSegment.getEndOffsetMs()).isNull();
        verify(segmentRepository, times(0)).saveAll(any());
    }

    @Test
    void 방송_세션_요약정보가_수신되면_정상적으로_구독자_비율과_피크_평균시청자가_업데이트되고_활성_세그먼트도_마감된다() {
        String streamId = "stream-summary";
        Instant startedAt = Instant.parse("2026-02-13T10:00:00Z");
        Instant endedAt = Instant.parse("2026-02-13T12:00:00Z");
        StreamSessionEntity session = new StreamSessionEntity(streamId, "test-live-id", "방제", "카테고리", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, "test-live-id", "방제", "카테고리", startedAt, 0L);

        when(sessionRepository.findActiveSession(streamId, "test-live-id"))
            .thenReturn(Optional.of(session));
        when(timelineRepository.findAverageViewerCountBySessionId("test-live-id"))
            .thenReturn(520.4);
        when(timelineRepository.findPeakViewerCountBySessionId("test-live-id"))
            .thenReturn(850);
        when(segmentRepository.findActiveSegment("test-live-id"))
            .thenReturn(Optional.of(activeSegment));

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
        when(sessionRepository.findActiveSession(streamId, "test-live-id"))
            .thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId("test-live-id"))
            .thenReturn(Optional.empty());

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

        when(sessionRepository.findActiveSession(streamId, "null-ended-id"))
            .thenReturn(Optional.of(session));
        when(timelineRepository.findAverageViewerCountBySessionId("null-ended-id"))
            .thenReturn(100.0);
        when(timelineRepository.findPeakViewerCountBySessionId("null-ended-id"))
            .thenReturn(200);
        when(segmentRepository.findActiveSegment("null-ended-id"))
            .thenReturn(Optional.of(activeSegment));

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
        Instant pastEndedAt = startedAt.minusSeconds(600); // 시작보다 10분 과거
        StreamSessionEntity session = new StreamSessionEntity(streamId, "invalid-ended-id", "방제", "카테고리", startedAt);
        StreamSessionSegmentEntity activeSegment = new StreamSessionSegmentEntity(streamId, "invalid-ended-id", "방제", "카테고리", startedAt, 0L);

        when(sessionRepository.findActiveSession(streamId, "invalid-ended-id"))
            .thenReturn(Optional.of(session));
        when(timelineRepository.findAverageViewerCountBySessionId("invalid-ended-id"))
            .thenReturn(100.0);
        when(timelineRepository.findPeakViewerCountBySessionId("invalid-ended-id"))
            .thenReturn(200);
        when(segmentRepository.findActiveSegment("invalid-ended-id"))
            .thenReturn(Optional.of(activeSegment));

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

        when(sessionRepository.findActiveSession(streamId, "test-live-id"))
            .thenReturn(Optional.empty());
        when(sessionRepository.findBySessionId("test-live-id"))
            .thenReturn(Optional.of(session));
        when(timelineRepository.findAverageViewerCountBySessionId("test-live-id"))
            .thenReturn(520.4);
        when(timelineRepository.findPeakViewerCountBySessionId("test-live-id"))
            .thenReturn(850);
        when(segmentRepository.findActiveSegment("test-live-id"))
            .thenReturn(Optional.empty());

        StreamSessionSummaryRequest request =
            new StreamSessionSummaryRequest(50.0, "test-live-id", accurateEndedAt);

        streamSessionService.updateSessionSummary(streamId, request);

        assertThat(session.getSubscriberChatRatio()).isEqualTo(50.0);
        assertThat(session.getEndedAt()).isEqualTo(accurateEndedAt);
        assertThat(session.getPeakViewers()).isEqualTo(850);
        assertThat(session.getAverageViewerCount()).isEqualTo(520);
    }
}
