package io.slice.stream.apiserver.stream.targeting;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.global.config.FilterConfig;
import io.slice.stream.apiserver.global.security.EngineTokenFilter;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TargetStreamerInternalController.class)
@Import({FilterConfig.class, EngineTokenFilter.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestPropertySource(properties = {
    "analysis.internal-prefix=/api/v1/internal",
    "analysis.header=X-ENGINE-SECRET",
    "analysis.secret=secret-key-123"
})
class TargetStreamerInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TargetStreamerService targetStreamerService;

    @Test
    void 유효한_인증_헤더로_요청하면_타겟_채널_목록을_200_OK로_반환한다() throws Exception {
        given(targetStreamerService.getActiveTargetChannelIds())
            .willReturn(List.of("channel-1", "channel-2", "channel-3"));

        mockMvc.perform(get("/api/v1/internal/targets")
                .header("X-ENGINE-SECRET", "secret-key-123")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0]").value("channel-1"))
            .andExpect(jsonPath("$[1]").value("channel-2"))
            .andExpect(jsonPath("$[2]").value("channel-3"));
    }

    @Test
    void 인증_헤더가_없으면_401_Unauthorized를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/internal/targets")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_인증_헤더이면_401_Unauthorized를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/internal/targets")
                .header("X-ENGINE-SECRET", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
}
