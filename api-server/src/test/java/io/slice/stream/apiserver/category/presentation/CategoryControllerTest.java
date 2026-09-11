package io.slice.stream.apiserver.category.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.category.application.CategoryQueryService;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@DisplayNameGeneration(ReplaceUnderscores.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @Test
    void 주간_인기_카테고리_랭킹_목록을_조회하면_200_OK와_목록을_반환한다() throws Exception {
        List<WeeklyCategoryResponse> mockResponses = List.of(
            new WeeklyCategoryResponse(1, "메이플스토리", "약 6.6만 시간", 65838L, "up", 2, "🍁"),
            new WeeklyCategoryResponse(2, "마인크래프트", "약 5.8만 시간", 57543L, "same", null, "⛏️")
        );

        given(categoryQueryService.getWeeklyCategoryRanking()).willReturn(mockResponses);

        mockMvc.perform(get("/api/v1/categories/weekly-ranking")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].categoryName").value("메이플스토리"))
            .andExpect(jsonPath("$[0].accumulatedViewHours").value("약 6.6만 시간"))
            .andExpect(jsonPath("$[0].exactHours").value(65838))
            .andExpect(jsonPath("$[0].change").value("up"))
            .andExpect(jsonPath("$[0].changeValue").value(2))
            .andExpect(jsonPath("$[0].icon").value("🍁"))
            .andExpect(jsonPath("$[1].rank").value(2))
            .andExpect(jsonPath("$[1].categoryName").value("마인크래프트"));
    }
}
