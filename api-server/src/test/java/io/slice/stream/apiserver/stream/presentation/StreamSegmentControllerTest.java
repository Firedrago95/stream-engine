package io.slice.stream.apiserver.stream.presentation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.slice.stream.apiserver.stream.application.StreamSessionService;
import io.slice.stream.apiserver.stream.application.dto.ChangedStreamRequest;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(StreamSegmentController.class)
@ActiveProfiles("test")
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamSegmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @MockitoBean
    private StreamSessionService streamSessionService;

    @Value("${analysis.meta.path}")
    private String metaPath;

    @Value("${analysis.header}")
    private String headerName;

    @Value("${analysis.secret}")
    private String secretValue;

    @Test
    void 변경_이벤트_수신_시_202_ACCEPTED를_반환하고_서비스를_호출한다() throws Exception {
        // given
        ChangedStreamRequest request = new ChangedStreamRequest(
            "stream1", 
            "test-live-id", 
            "이전방제", 
            "새로운방제", 
            "이전카테고리", 
            "새로운카테고리",
            Instant.now(), 
            1000L
        );
        String requestBody = objectMapper.writeValueAsString(List.of(request));

        // when & then
        mockMvc.perform(post(metaPath)
                .header(headerName, secretValue)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isAccepted());

        verify(streamSessionService, times(1)).updateSessionSegment(anyList());
    }
}
