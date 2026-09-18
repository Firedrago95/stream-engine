package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.streamer.application.StreamerFollowerQueryService;
import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StreamerFollowerQueryController.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerFollowerQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamerFollowerQueryService followerQueryService;

    @Test
    void 팔로워_트렌드_목록을_200_OK로_반환한다() throws Exception {
        FollowerTrendResponse item = new FollowerTrendResponse(LocalDate.of(2026, 9, 17), 10500, 100);
        given(followerQueryService.getFollowerTrend("ch1", 30))
            .willReturn(List.of(item));

        mockMvc.perform(get("/api/v1/streamers/ch1/follower-trend?days=30")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].date").value("2026-09-17"))
            .andExpect(jsonPath("$[0].followerCount").value(10500))
            .andExpect(jsonPath("$[0].followerGrowth").value(100));
    }
}
