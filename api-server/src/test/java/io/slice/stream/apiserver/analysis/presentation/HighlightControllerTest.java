package io.slice.stream.apiserver.analysis.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.analysis.application.service.HighlightQueryService;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import java.time.Instant;
import java.time.LocalDate;
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
    void 특정_날짜_파라미터로_하이라이트_목록을_조회한다() throws Exception {
        // given
        String streamId = "stream-123";
        LocalDate date = LocalDate.of(2026, 3, 4);
        List<HighlightResponse> mockResponses = List.of(
            new HighlightResponse(1L, streamId, "FINISHED", Instant.now().minusSeconds(100), Instant.now().minusSeconds(50), 500L, 50L)
        );

        given(highlightQueryService.getHighlightsByDate(streamId, date)).willReturn(mockResponses);

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/highlights", streamId)
                .param("date", "2026-03-04"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].streamId").value(streamId));
    }

    @Test
    void 날짜_파라미터가_없으면_오늘_날짜를_기본으로_조회한다() throws Exception {
        // given
        String streamId = "stream-123";
        given(highlightQueryService.getHighlightsByDate(eq(streamId), any(LocalDate.class)))
            .willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/analysis/streams/{streamId}/highlights", streamId))
            .andExpect(status().isOk());

        // LocalDate.now()가 인자로 넘어갔는지 확인
        verify(highlightQueryService).getHighlightsByDate(eq(streamId), eq(LocalDate.now()));
    }
}
