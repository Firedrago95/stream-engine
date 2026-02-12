package io.slice.stream.apiserver.aggregation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.aggregation.application.ChatAggregationService;
import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult;
import io.slice.stream.apiserver.aggregation.domain.ChatAggregationResult.DataPoint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatAggregationController.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChatAggregationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatAggregationService chatAggregationService;

    @Test
    void 스트림_ID로_채팅_집계_기록을_조회_할_수_있다() throws Exception {
        // given
        String streamId = "abcde12345";
        ChatAggregationResult aggregationResult = new ChatAggregationResult(streamId, List.of(
            new DataPoint(1700000000000L, 10),
            new DataPoint(1700000000010L, 25)
        ));

        when(chatAggregationService.getChatAggregationResult(streamId)).thenReturn(Optional.of(aggregationResult));

        // when & then
        mockMvc.perform(get("/api/v1/aggregation/{streamId}", streamId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.streamId").value(streamId))
            .andExpect(jsonPath("$.dataPoints.length()").value(2))
            .andExpect(jsonPath("$.dataPoints[0].timestamp").value(1700000000000L))
            .andExpect(jsonPath("$.dataPoints[0].value").value(10))
            .andExpect(jsonPath("$.dataPoints[1].timestamp").value(1700000000010L))
            .andExpect(jsonPath("$.dataPoints[1].value").value(25));
    }

    @Test
    void 채팅_집계_기록이_없으면_NOT_FOUND를_반환한다() throws Exception {
        // given
        String streamId = "notFound";
        when(chatAggregationService.getChatAggregationResult(streamId)).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/v1/aggregation/{streamId}", streamId))
            .andExpect(status().isNotFound());
    }
}
