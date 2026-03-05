package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.analysis.application.service.AnalysisQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisQueryController.class)
class AnalysisQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisQueryService analysisQueryService;

    @Test
    void 조회_요청_시_서비스를_통해_데이터를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        AnalysisResponse response = new AnalysisResponse(streamId, List.of(
            new AnalysisDataPoint(System.currentTimeMillis(), 100L, "NORMAL")
        ));
        given(analysisQueryService.getRecentAnalysis(streamId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/{streamId}", streamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataPoints[0].status").value("NORMAL"));
    }

    @Test
    void 가용_날짜_목록_조회_API_호출시_결과를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        LocalDate before = LocalDate.of(2026, 3, 5);
        int limit = 10;

        given(analysisQueryService.getAvailableDates(streamId, before, limit))
            .willReturn(List.of("2026-03-05", "2026-03-04"));

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/available-dates", streamId)
                .param("before", "2026-03-05")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("2026-03-05"))
            .andExpect(jsonPath("$[1]").value("2026-03-04"));
    }

    @Test
    void 과거_데이터_차트_조회_API_호출시_결과를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        LocalDate date = LocalDate.of(2026, 3, 5);

        AnalysisResponse response = new AnalysisResponse(streamId, List.of(
            new AnalysisDataPoint(System.currentTimeMillis(), 150L, "PEAK")
        ));
        given(analysisQueryService.getHistoryAnalysis(streamId, date)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/history", streamId)
                .param("date", "2026-03-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.streamId").value(streamId))
            .andExpect(jsonPath("$.dataPoints[0].status").value("PEAK"));
    }
}
