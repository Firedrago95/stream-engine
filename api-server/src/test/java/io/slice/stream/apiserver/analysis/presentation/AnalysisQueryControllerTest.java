package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.analysis.application.service.AnalysisQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import java.time.Instant;
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
            // DTO에 추가된 offsetMs(4번째 인자) 맞춰서 0L 추가
            new AnalysisDataPoint(System.currentTimeMillis(), 100L, "NORMAL", 0L)
        ));
        given(analysisQueryService.getRecentAnalysis(streamId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/{streamId}", streamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataPoints[0].status").value("NORMAL"));
    }

    @Test
    void 가용_세션_목록_조회_API_호출시_결과를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        int limit = 10;
        Instant startedAt1 = Instant.parse("2026-03-17T20:30:00Z");
        Instant startedAt2 = Instant.parse("2026-03-16T20:11:00Z");

        List<SessionResponse> mockSessions = List.of(
            new SessionResponse("session-1", startedAt1),
            new SessionResponse("session-2", startedAt2)
        );

        given(analysisQueryService.getAvailableSessions(streamId, limit))
            .willReturn(mockSessions);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/available-sessions", streamId)
                .param("limit", String.valueOf(limit)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessionId").value("session-1"))
            .andExpect(jsonPath("$[0].startedAt").value(startedAt1.toString()))
            .andExpect(jsonPath("$[1].sessionId").value("session-2"))
            .andExpect(jsonPath("$[1].startedAt").value(startedAt2.toString()));
    }

    @Test
    void 과거_데이터_차트_조회_API_호출시_결과를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        String sessionId = "target-session-id";

        AnalysisResponse response = new AnalysisResponse(streamId, List.of(
            new AnalysisDataPoint(System.currentTimeMillis(), 150L, "PEAK", 5000L)
        ));
        given(analysisQueryService.getHistoryAnalysis(streamId, sessionId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/history", streamId)
                .param("sessionId", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.streamId").value(streamId))
            .andExpect(jsonPath("$.dataPoints[0].status").value("PEAK"))
            .andExpect(jsonPath("$.dataPoints[0].value").value(150));
    }
}
