package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.global.config.FilterConfig;
import io.slice.stream.apiserver.global.security.EngineTokenFilter;
import io.slice.stream.apiserver.streamer.application.StreamerFollowerCommandService;
import io.slice.stream.apiserver.streamer.application.dto.FollowerSnapshotRecordDto;
import java.time.LocalDate;
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
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(StreamerFollowerInternalController.class)
@Import({FilterConfig.class, EngineTokenFilter.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
@TestPropertySource(properties = {
    "analysis.internal-prefix=/api/v1/internal",
    "analysis.header=X-ENGINE-SECRET",
    "analysis.secret=secret-key-123"
})
class StreamerFollowerInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamerFollowerCommandService followerCommandService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 유효한_인증_헤더로_요청하면_팔로워_수집_대상_목록을_200_OK로_반환한다() throws Exception {
        given(followerCommandService.findFollowerTargetChannelIds())
            .willReturn(List.of("channel-1", "channel-2"));

        mockMvc.perform(get("/api/v1/internal/follower-targets")
                .header("X-ENGINE-SECRET", "secret-key-123")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0]").value("channel-1"))
            .andExpect(jsonPath("$[1]").value("channel-2"));
    }

    @Test
    void 팔로워_스냅샷_벌크_데이터를_성공적으로_수신하여_저장한다() throws Exception {
        List<FollowerSnapshotRecordDto> dtos = List.of(
            new FollowerSnapshotRecordDto("ch1", 10000, LocalDate.of(2026, 9, 17))
        );

        mockMvc.perform(post("/api/v1/internal/follower-snapshots")
                .header("X-ENGINE-SECRET", "secret-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dtos)))
            .andExpect(status().isOk());

        verify(followerCommandService).recordFollowers(anyList());
    }

    @Test
    void 인증_헤더가_누락되면_401_Unauthorized를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/internal/follower-targets"))
            .andExpect(status().isUnauthorized());
    }
}
