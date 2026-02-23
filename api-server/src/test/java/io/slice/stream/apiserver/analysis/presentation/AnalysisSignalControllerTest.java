package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.apiserver.analysis.application.AnalysisService;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisSignalRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalysisSignalController.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestPropertySource(properties = "analysis.signal.path=/api/v1/signals/test-path")
class AnalysisSignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void 엔진으로부터_분석_신호를_받으면_202_Accepted를_반환하고_서비스를_호출한다() throws Exception {
        // given
        String signalPath = "/api/v1/signals/test-path";
        List<AnalysisSignalRequest> requests = List.of(
            new AnalysisSignalRequest("stream1", "PEAK", Instant.now(), 10)
        );

        // when & then
        mockMvc.perform(post(signalPath)
                .header("X-SL-ENGINE-TOKEN", "test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
            .andExpect(status().isAccepted());

        verify(analysisService).processSignals(anyList());
    }
}
