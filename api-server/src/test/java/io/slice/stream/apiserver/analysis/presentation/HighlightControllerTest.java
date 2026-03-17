package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.analysis.application.service.HighlightQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HighlightController.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class HighlightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HighlightQueryService highlightQueryService;

    @Test
    void 특정_세션_파라미터로_하이라이트_목록을_조회한다() throws Exception {
        // given
        String streamId = "stream-123";
        String sessionId = "target-session";

        Instant start = Instant.now().minusSeconds(100);
        Instant end = start.plusSeconds(50);
        long startOffset = 3600000L; // 1시간 지점
        long endOffset = 3650000L;

        List<HighlightResponse> mockResponses = List.of(
            new HighlightResponse(
                1L,
                streamId,
                start,
                end,
                50L,
                startOffset,
                endOffset,
                500L,
                "FINISHED"
            )
        );

        given(highlightQueryService.getHighlightsBySessionId(streamId, sessionId)).willReturn(mockResponses);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/highlights", streamId)
                .param("sessionId", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].streamId").value(streamId))
            .andExpect(jsonPath("$[0].startTimeOffset").value(startOffset))
            .andExpect(jsonPath("$[0].endTimeOffset").value(endOffset))
            .andExpect(jsonPath("$[0].durationSeconds").value(50));
    }

    @Test
    void 세션_파라미터가_없으면_null을_전달하여_실시간_조회를_수행한다() throws Exception {
        // given
        String streamId = "stream-123";

        // 파라미터가 없을 때 컨트롤러는 sessionId에 null을 담아서 서비스에 넘김
        given(highlightQueryService.getHighlightsBySessionId(streamId, null))
            .willReturn(List.of());

        // when & then (파라미터 없이 요청)
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/highlights", streamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // 서비스의 getHighlightsBySessionId에 null이 제대로 전달되었는지 검증
        verify(highlightQueryService).getHighlightsBySessionId(streamId, null);
    }
}
