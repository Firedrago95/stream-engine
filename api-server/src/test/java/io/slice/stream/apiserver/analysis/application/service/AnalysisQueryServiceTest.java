package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisQueryServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private JpaStreamSessionSegmentRepository segmentRepository;

    @Mock
    private JpaViewMetricTimelineRepository timelineRepository;

    @InjectMocks
    private AnalysisQueryService analysisQueryService;

    @Test
    void 특정_스트림의_최근_분석_데이터를_조회하면_DTO_형태로_변환하여_반환한다() {
        String streamId = "test-stream";
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of(streamId, "sessionId", "PEAK", now, 100L, 1000L)
        );
        StreamSessionEntity activeSession = new StreamSessionEntity(streamId, "sessionId", "실시간 방송", "Just Chatting", now.minusSeconds(600));
        activeSession.updatePeakViewers(1500);

        ViewMetricTimelineEntity timeline = new ViewMetricTimelineEntity(streamId, "sessionId", now, 1500);

        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(signals);
        given(sessionRepository.findActiveSession(streamId)).willReturn(Optional.of(activeSession));
        given(timelineRepository.findBySessionIdOrderByTimestampAsc("sessionId")).willReturn(List.of(timeline));

        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(100L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");
        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(1500);
        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().peakViewers()).isEqualTo(1500);
    }

    @Test
    void 과거_데이터_조회_시_요약_데이터가_존재하면_이를_우선적으로_반환한다() {
        // given
        String streamId = "test-stream";
        String sessionId = "target-session"; // 날짜(LocalDate) -> 세션(sessionId)으로 변경
        List<AnalysisDataPoint> summaryPoints = List.of(
            new AnalysisDataPoint(1000L, 150L, "NORMAL", 5000L)
        );

        given(segmentRepository.findBySessionIdOrderByStartedAtAsc(sessionId))
            .willReturn(List.of());
        given(analysisRepository.findSummaryHistory(streamId, sessionId))
            .willReturn(summaryPoints);

        // when
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        // then
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(150L);
        assertThat(response.segments()).isEmpty();

        verify(analysisRepository).findSummaryHistory(streamId, sessionId);
        verify(analysisRepository, never()).findRawHistory(any(), any());
    }

    @Test
    void 과거_데이터_조회_시_요약_데이터가_비어있으면_원본_데이터를_1분_단위로_압축하여_반환한다() {
        // given
        String streamId = "test-stream";
        String sessionId = "target-session";

        // 0분대 데이터 2개 (ms: 1000, 1500) -> 평균: 250, 상태: PEAK 우선
        // 1분대 데이터 1개 (ms: 65000) -> 평균: 50, 상태: NORMAL
        List<AnalysisDataPoint> rawPoints = List.of(
            new AnalysisDataPoint(1000L, 200L, "NORMAL", 1000L),
            new AnalysisDataPoint(1500L, 300L, "PEAK", 1500L),
            new AnalysisDataPoint(65000L, 50L, "NORMAL", 65000L)
        );

        given(segmentRepository.findBySessionIdOrderByStartedAtAsc(sessionId))
            .willReturn(List.of());
        given(analysisRepository.findSummaryHistory(streamId, sessionId))
            .willReturn(List.of()); // 요약 데이터 없음
        given(analysisRepository.findRawHistory(streamId, sessionId))
            .willReturn(rawPoints); // 원본 데이터 있음

        // when
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        // then
        List<AnalysisDataPoint> points = response.dataPoints();

        // 3개의 원본 데이터가 2개의 1분 단위 데이터로 묶여야 함
        assertThat(points).hasSize(2);

        // 첫 번째 1분 (0 ~ 59999ms) 검증
        assertThat(points.get(0).timestamp()).isEqualTo(0L); // timestamp 기준 (offset 아님)
        assertThat(points.get(0).value()).isEqualTo(250L);
        assertThat(points.get(0).status()).isEqualTo("PEAK");

        // 두 번째 1분 (60000 ~ 119999ms) 검증
        assertThat(points.get(1).timestamp()).isEqualTo(60000L);
        assertThat(points.get(1).value()).isEqualTo(50L);
        assertThat(points.get(1).status()).isEqualTo("NORMAL");
        assertThat(response.segments()).isEmpty();

        verify(analysisRepository).findSummaryHistory(streamId, sessionId);
        verify(analysisRepository).findRawHistory(streamId, sessionId);
    }

    @Test
    void 세션_목록_조회_시_상세_지표와_함께_반환한다() {
        String streamId = "test-stream";
        int limit = 10;
        Instant startedAt = Instant.parse("2026-03-17T11:30:00Z");

        StreamSessionEntity sessionEntity = new StreamSessionEntity(streamId, "session-123", "방제", "게임", startedAt);
        sessionEntity.updatePeakViewers(3500);
        sessionEntity.finishSession(startedAt.plusSeconds(3600), 3500, 2100);
        sessionEntity.updateSubscriberChatRatio(35.5);

        given(sessionRepository.findRecentSessionsByStreamId(eq(streamId), any(Pageable.class)))
            .willReturn(List.of(sessionEntity));

        List<SessionResponse> sessions = analysisQueryService.getAvailableSessions(streamId, limit);

        assertThat(sessions).hasSize(1);
        SessionResponse session = sessions.get(0);
        assertThat(session.sessionId()).isEqualTo("session-123");
        assertThat(session.title()).isEqualTo("방제");
        assertThat(session.categoryName()).isEqualTo("게임");
        assertThat(session.startedAt()).isEqualTo(startedAt);
        assertThat(session.peakViewers()).isEqualTo(3500);
        assertThat(session.averageViewerCount()).isEqualTo(2100);
        assertThat(session.subscriberChatRatio()).isEqualTo(35.5);

        verify(sessionRepository).findRecentSessionsByStreamId(eq(streamId), any(Pageable.class));
    }

    @Test
    void 과거_데이터_조회_시_세그먼트와_시계열_타임라인_및_세션_요약이_함께_반환된다() {
        String streamId = "test-stream";
        String sessionId = "target-session";
        Instant segmentStart = Instant.now();
        StreamSessionSegmentEntity segmentEntity = new StreamSessionSegmentEntity(
            streamId, sessionId, "테스트 방제", "테스트 카테고리", segmentStart, 0L
        );
        segmentEntity.endSegment(segmentStart.plusSeconds(30), 30000L);

        StreamSessionEntity sessionEntity = new StreamSessionEntity(streamId, sessionId, "테스트 방제", "테스트 카테고리", segmentStart);
        sessionEntity.finishSession(segmentStart.plusSeconds(3600), 2500, 1800);
        sessionEntity.updateSubscriberChatRatio(50.0);

        ViewMetricTimelineEntity timelineEntity = new ViewMetricTimelineEntity(streamId, sessionId, segmentStart, 2000);

        given(segmentRepository.findBySessionIdOrderByStartedAtAsc(sessionId))
            .willReturn(List.of(segmentEntity));
        given(timelineRepository.findBySessionIdOrderByTimestampAsc(sessionId))
            .willReturn(List.of(timelineEntity));
        given(sessionRepository.findBySessionId(sessionId))
            .willReturn(Optional.of(sessionEntity));
        given(analysisRepository.findSummaryHistory(streamId, sessionId))
            .willReturn(List.of(new AnalysisDataPoint(1000L, 100L, "NORMAL", 0L)));

        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).title()).isEqualTo("테스트 방제");
        assertThat(response.segments().get(0).categoryName()).isEqualTo("테스트 카테고리");
        assertThat(response.segments().get(0).startedAt()).isEqualTo(segmentStart);
        assertThat(response.segments().get(0).endedAt()).isEqualTo(segmentStart.plusSeconds(30));
        assertThat(response.segments().get(0).startOffsetMs()).isEqualTo(0L);
        assertThat(response.segments().get(0).endOffsetMs()).isEqualTo(30000L);

        assertThat(response.timeline()).hasSize(1);
        assertThat(response.timeline().get(0).viewerCount()).isEqualTo(2000);

        assertThat(response.summary()).isNotNull();
        assertThat(response.summary().peakViewers()).isEqualTo(2500);
        assertThat(response.summary().averageViewerCount()).isEqualTo(1800);
        assertThat(response.summary().subscriberChatRatio()).isEqualTo(50.0);

        verify(segmentRepository).findBySessionIdOrderByStartedAtAsc(sessionId);
        verify(timelineRepository).findBySessionIdOrderByTimestampAsc(sessionId);
        verify(sessionRepository).findBySessionId(sessionId);
    }
}
