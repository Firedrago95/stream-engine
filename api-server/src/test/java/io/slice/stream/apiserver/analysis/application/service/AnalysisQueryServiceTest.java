package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.Instant;
import java.util.List;
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
    private JpaStreamSessionRepository sessionRepository; // [추가] 탭 목록 조회용

    @InjectMocks
    private AnalysisQueryService analysisQueryService;

    @Test
    void 특정_스트림의_최근_분석_데이터를_조회하면_DTO_형태로_변환하여_반환한다() {
        // given
        String streamId = "test-stream";
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of(streamId, "sessionId", "PEAK", now, 100L, 1000L)
        );
        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(signals);

        // when
        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(100L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");
    }

    @Test
    void 과거_데이터_조회_시_요약_데이터가_존재하면_이를_우선적으로_반환한다() {
        // given
        String streamId = "test-stream";
        String sessionId = "target-session"; // 날짜(LocalDate) -> 세션(sessionId)으로 변경
        List<AnalysisDataPoint> summaryPoints = List.of(
            new AnalysisDataPoint(1000L, 150L, "NORMAL", 5000L)
        );

        given(analysisRepository.findSummaryHistory(streamId, sessionId))
            .willReturn(summaryPoints);

        // when
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, sessionId);

        // then
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(150L);

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

        verify(analysisRepository).findSummaryHistory(streamId, sessionId);
        verify(analysisRepository).findRawHistory(streamId, sessionId);
    }

    @Test
    void 세션_목록_조회_시_시작_시간과_함께_반환한다() {
        // given
        String streamId = "test-stream";
        int limit = 10;

        // 시간: 2026-03-17T20:30:00 KST
        Instant startedAt = Instant.parse("2026-03-17T11:30:00Z");

        StreamSessionEntity sessionEntity = new StreamSessionEntity(streamId, "session-123", "방제", "게임", startedAt);

        given(sessionRepository.findRecentSessionsByStreamId(eq(streamId), any(Pageable.class)))
            .willReturn(List.of(sessionEntity));

        // when
        List<SessionResponse> sessions = analysisQueryService.getAvailableSessions(streamId, limit);

        // then
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo("session-123");
        // 포맷팅 검증 ("M월 d일 HH:mm 방송")
        assertThat(sessions.get(0).startedAt()).isEqualTo(startedAt);

        verify(sessionRepository).findRecentSessionsByStreamId(eq(streamId), any(Pageable.class));
    }
}
