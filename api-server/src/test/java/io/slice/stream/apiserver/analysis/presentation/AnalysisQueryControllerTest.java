package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.slice.stream.apiserver.analysis.application.AnalysisService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
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
    private AnalysisService analysisService;

    @Test
    void 조회_요청_시_서비스를_통해_데이터를_반환한다() throws Exception {
        // given
        String streamId = "test-stream";
        AnalysisResponse response = new AnalysisResponse(streamId, List.of(
            new AnalysisDataPoint(System.currentTimeMillis(), 100L, "NORMAL")
        ));
        given(analysisService.getRecentAnalysis(streamId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/{streamId}", streamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataPoints[0].status").value("NORMAL"));
    }
}
