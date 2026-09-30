package io.slice.stream.engine.sampler.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.engine.sampler.application.ChatSamplerService;
import io.slice.stream.engine.sampler.application.dto.SamplingStatusResponse;
import io.slice.stream.engine.sampler.application.dto.StartSamplingRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatSamplerInternalControllerTest {

    @Mock
    private ChatSamplerService chatSamplerService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ChatSamplerInternalController controller = new ChatSamplerInternalController(chatSamplerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 샘플링_시작_요청이_성공하면_200_OK를_반환한다() throws Exception {
        StartSamplingRequest request = new StartSamplingRequest(
                "lol_peak",
                List.of("ch1"),
                null,
                null,
                30
        );

        mockMvc.perform(post("/api/internal/chat-sampler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("채팅 샘플링이 시작되었습니다."));

        verify(chatSamplerService).startSampling(any(StartSamplingRequest.class));
    }

    @Test
    void 샘플링_중단_요청_시_200_OK를_반환한다() throws Exception {
        mockMvc.perform(post("/api/internal/chat-sampler/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("모든 채팅 샘플링이 중단되었습니다."));

        verify(chatSamplerService).stopAll(any());
    }

    @Test
    void 샘플링_상태_조회가_정상_동작한다() throws Exception {
        SamplingStatusResponse mockResponse = new SamplingStatusResponse(true, 1, 0, List.of());
        given(chatSamplerService.getStatus()).willReturn(mockResponse);

        mockMvc.perform(get("/api/internal/chat-sampler/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.activeSessionCount").value(1));
    }
}
