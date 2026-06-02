package io.slice.stream.apiserver.stream.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.apiserver.stream.application.StreamSessionService;
import io.slice.stream.apiserver.stream.presentation.dto.StreamSessionSummaryRequest;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StreamSessionSummaryController.class)
@ActiveProfiles("test")
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSessionSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @MockitoBean
    private StreamSessionService streamSessionService;

    @Value("${analysis.summary.path}")
    private String summaryPath;

    @Value("${analysis.header}")
    private String headerName;

    @Value("${analysis.secret}")
    private String secretValue;

    @Test
    void 요약_정보_전송_API_호출시_200_OK를_반환한다() throws Exception {
        // given
        String streamId = "test-stream-id";
        StreamSessionSummaryRequest request = new StreamSessionSummaryRequest(35.5);
        
        doNothing().when(streamSessionService).updateSessionSummary(eq(streamId), any(StreamSessionSummaryRequest.class));

        // when & then
        mockMvc.perform(post(summaryPath + "/{streamId}", streamId)
                .header(headerName, secretValue)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
}
