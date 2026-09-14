package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.streamer.application.StreamerGrassQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerProfileQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSessionQueryService;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerHeaderDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerKpiSummaryDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StreamerQueryController.class)
@ActiveProfiles("test")
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamerLeaderboardQueryService leaderboardQueryService;

    @MockitoBean
    private StreamerProfileQueryService profileQueryService;

    @MockitoBean
    private StreamerGrassQueryService grassQueryService;

    @MockitoBean
    private StreamerSessionQueryService sessionQueryService;

    @Test
    void 스트리머_리더보드_조회_API_호출시_200_OK와_목록을_반환한다() throws Exception {
        StreamResponse item = new StreamResponse(
            "ch_test",
            "테스트스트리머",
            "방송 방제",
            "https://img.png",
            "토크",
            1200,
            StreamStatus.LIVE,
            3500
        );
        given(leaderboardQueryService.getLeaderboard(null)).willReturn(List.of(item));

        mockMvc.perform(get("/api/v1/streamers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].streamId").value("ch_test"))
            .andExpect(jsonPath("$[0].streamerName").value("테스트스트리머"))
            .andExpect(jsonPath("$[0].status").value("LIVE"))
            .andExpect(jsonPath("$[0].concurrentUserCount").value(1200))
            .andExpect(jsonPath("$[0].averageViewers").value(3500));
    }

    @Test
    void 프로필_조회_API_호출시_200_OK와_프로필_정보를_반환한다() throws Exception {
        String channelId = "ch_profile_test";
        StreamerProfileResponse response = new StreamerProfileResponse(
            new StreamerHeaderDto(channelId, "침착맨", "https://img.png", true, 120000, 300, 1500),
            new StreamerKpiSummaryDto(4500, 12000, 150000L, 187.5, 1500),
            Collections.emptyList()
        );

        given(profileQueryService.getProfile(channelId)).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/profile", channelId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.header.channelId").value(channelId))
            .andExpect(jsonPath("$.header.streamerName").value("침착맨"))
            .andExpect(jsonPath("$.summary.peakViewers").value(12000));
    }

    @Test
    void 잔디_조회_API_호출시_200_OK와_잔디_목록을_반환한다() throws Exception {
        String channelId = "ch_grass_test";
        StreamerGrassResponse response = new StreamerGrassResponse(
            channelId, 5, 100000L, 20, List.of()
        );

        given(grassQueryService.getGrassData(eq(channelId), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/grass", channelId)
                .param("days", "90"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(channelId))
            .andExpect(jsonPath("$.currentStreak").value(5));
    }

    @Test
    void 잔디_조회_일수가_범위를_벗어나면_400_Bad_Request를_반환한다() throws Exception {
        String channelId = "ch_invalid_days";
        given(grassQueryService.getGrassData(channelId, 5))
            .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "잔디 조회 일수 오류"));

        mockMvc.perform(get("/api/v1/streamers/{channelId}/grass", channelId)
                .param("days", "5"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 세션_전적_조회_API_호출시_200_OK와_페이징_결과를_반환한다() throws Exception {
        String channelId = "ch_session_test";
        StreamerSessionHistoryResponse response = new StreamerSessionHistoryResponse(
            Collections.emptyList(), 0, 10, 0, 0, false
        );

        given(sessionQueryService.getSessionHistory(channelId, 0, 10)).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/sessions", channelId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));
    }
}
