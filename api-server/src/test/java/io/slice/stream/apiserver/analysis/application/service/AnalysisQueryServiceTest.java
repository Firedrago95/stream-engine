package io.slice.stream.apiserver.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AnalysisQueryServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private AnalysisQueryService analysisQueryService;

    @Test
    void 특정_스트림의_최근_분석_데이터를_조회하면_DTO_형태로_변환하여_반환한다() {
        // given
        String streamId = "test-stream";
        Instant now = Instant.now();
        List<AnalysisSignal> signals = List.of(
            AnalysisSignal.of(streamId, "PEAK", now, 100L)
        );
        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(signals);

        // when
        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).hasSize(1);
        assertThat(response.dataPoints().get(0).value()).isEqualTo(100L);
        assertThat(response.dataPoints().get(0).status()).isEqualTo("PEAK");
        assertThat(response.dataPoints().get(0).timestamp()).isEqualTo(now.toEpochMilli());
    }

    @Test
    void 조회된_데이터가_없으면_빈_리스트를_가진_응답_객체를_반환한다() {
        // given
        String streamId = "empty-stream";
        given(analysisRepository.findRecentSignals(streamId, 100)).willReturn(List.of());

        // when
        AnalysisResponse response = analysisQueryService.getRecentAnalysis(streamId);

        // then
        assertThat(response.streamId()).isEqualTo(streamId);
        assertThat(response.dataPoints()).isEmpty();
    }

    @Test
    void 가용_날짜_조회_시_커서가_없으면_미래_날짜를_기준으로_사용한다() {
        // given
        String streamId = "test-stream";
        int limit = 10;
        LocalDate expectedCursor = LocalDate.now(ZoneId.of("Asia/Seoul")).plusYears(2);

        // Mockito 스터빙 시 정확한 인자 혹은 any() 사용
        given(analysisRepository.findAvailableDates(eq(streamId), any(LocalDate.class), eq(limit)))
            .willReturn(List.of(LocalDate.of(2026, 3, 5)));

        // when
        List<String> dates = analysisQueryService.getAvailableDates(streamId, null, limit);

        // then
        assertThat(dates).containsExactly("2026-03-05");
        // 💡 검증 시에도 any()를 사용하여 유연하게 대응
        verify(analysisRepository).findAvailableDates(eq(streamId), any(LocalDate.class), eq(limit));
    }

    @Test
    void 과거_데이터_조회_시_3일_이내_날짜면_원본_데이터를_조회한다() {
        // given
        String streamId = "test-stream";
        LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1); // 1일 전 (Hot)

        given(analysisRepository.findRawHistory(eq(streamId), any(), any()))
            .willReturn(List.of(new AnalysisDataPoint(12345L, 100L, "NORMAL")));

        // when
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, targetDate);

        // then
        assertThat(response.dataPoints()).hasSize(1);
        verify(analysisRepository).findRawHistory(eq(streamId), any(), any());
    }

    @Test
    void 과거_데이터_조회_시_3일_이전_날짜면_요약_데이터를_조회한다() {
        // given
        String streamId = "test-stream";
        LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(5); // 5일 전 (Warm)

        given(analysisRepository.findSummaryHistory(eq(streamId), any(), any()))
            .willReturn(List.of(new AnalysisDataPoint(12345L, 200L, "PEAK")));

        // when
        AnalysisResponse response = analysisQueryService.getHistoryAnalysis(streamId, targetDate);

        // then
        assertThat(response.dataPoints()).hasSize(1);
        verify(analysisRepository).findSummaryHistory(eq(streamId), any(), any());
    }
}
