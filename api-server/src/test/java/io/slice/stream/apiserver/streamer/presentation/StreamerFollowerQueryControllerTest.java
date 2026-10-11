package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.streamer.application.StreamerFollowerQueryService;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingResponse;
import io.slice.stream.apiserver.streamer.application.dto.FollowerRankingType;
import io.slice.stream.apiserver.streamer.application.dto.FollowerTrendResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StreamerFollowerQueryControllerTest {

    @Mock
    private StreamerFollowerQueryService followerQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamerFollowerQueryController controller = new StreamerFollowerQueryController(followerQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("팔로워 트렌드 조회가 정상적으로 200 OK를 반환한다")
    void getFollowerTrend() throws Exception {
        given(followerQueryService.getFollowerTrend("ch1", 30))
            .willReturn(List.of(new FollowerTrendResponse(LocalDate.of(2026, 10, 1), 1000, 50)));

        mockMvc.perform(get("/api/v1/streamers/ch1/follower-trend?days=30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].followerCount").value(1000))
            .andExpect(jsonPath("$[0].followerGrowth").value(50));
    }

    @Test
    @DisplayName("팔로워 리더보드 조회가 정상적으로 200 OK를 반환한다")
    void getFollowerLeaderboard() throws Exception {
        FollowerRankingResponse item = new FollowerRankingResponse(
            "ch1", "스트리머1", "방제", "https://img.png", "토크",
            StreamStatus.LIVE, 1500, 50000, 3200
        );
        given(followerQueryService.getFollowerLeaderboard(FollowerRankingType.GROWTH, 20))
            .willReturn(List.of(item));

        mockMvc.perform(get("/api/v1/streamers/leaderboard/followers?type=GROWTH&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].streamId").value("ch1"))
            .andExpect(jsonPath("$[0].streamerName").value("스트리머1"))
            .andExpect(jsonPath("$[0].weeklyGrowth").value(3200))
            .andExpect(jsonPath("$[0].status").value("LIVE"));
    }
}
