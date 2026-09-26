package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.streamer.application.StreamerCalendarQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerProfileQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSessionQueryService;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse.CalendarSessionDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerHeaderDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerKpiSummaryDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse;
import java.time.Instant;
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
    private StreamerCalendarQueryService calendarQueryService;

    @MockitoBean
    private StreamerSessionQueryService sessionQueryService;

    @Test
    void 스트리머_리더보드_조회_API_호출시_200_OK와_목록을_반환한다() throws Exception {
        StreamResponse stream = new StreamResponse(
            "ch_123", "침착맨", "소통", "https://img.png", "Just Chatting", 10000,
            StreamStatus.LIVE, 8000
        );

        given(leaderboardQueryService.getLeaderboard(null)).willReturn(List.of(stream));

        mockMvc.perform(get("/api/v1/streamers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].streamId").value("ch_123"))
            .andExpect(jsonPath("$[0].streamerName").value("침착맨"));
    }

    @Test
    void 스트리머_상세_프로필_조회_API_호출시_200_OK와_데이터를_반환한다() throws Exception {
        String channelId = "ch_detail";
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
    void 월간_캘린더_조회_API_호출시_200_OK와_세션_목록을_반환한다() throws Exception {
        String channelId = "ch_cal_test";
        CalendarSessionDto sessionDto = new CalendarSessionDto(
            "sess_1", "롤 한판ㄴ", "League of Legends",
            Instant.parse("2026-09-10T11:23:00Z"),
            Instant.parse("2026-09-10T21:59:00Z"),
            38160L, 5746, 3500, false
        );
        StreamerCalendarResponse response = new StreamerCalendarResponse(
            channelId, 2026, 9, List.of(sessionDto)
        );

        given(calendarQueryService.getMonthlyCalendar(eq(channelId), eq(2026), eq(9))).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/calendar", channelId)
                .param("year", "2026")
                .param("month", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(channelId))
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.month").value(9))
            .andExpect(jsonPath("$.sessions[0].sessionId").value("sess_1"))
            .andExpect(jsonPath("$.sessions[0].title").value("롤 한판ㄴ"));
    }

    @Test
    void 월간_캘린더_조회시_유효하지_않은_월이면_400_Bad_Request를_반환한다() throws Exception {
        String channelId = "ch_invalid_cal";
        given(calendarQueryService.getMonthlyCalendar(eq(channelId), anyInt(), eq(13)))
            .willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조회 월 오류"));

        mockMvc.perform(get("/api/v1/streamers/{channelId}/calendar", channelId)
                .param("year", "2026")
                .param("month", "13"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 세션_전적_조회_API_호출시_200_OK와_페이징_결과를_반환한다() throws Exception {
        String channelId = "ch_session_test";
        StreamerSessionHistoryResponse response = new StreamerSessionHistoryResponse(
            Collections.emptyList(), 0, 10, 0, 0, false
        );

        given(sessionQueryService.getSessionHistory(channelId, 0, 10, false)).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/sessions", channelId)
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void 세션_전적_조회_API_호출시_paidPromotionOnly_파라미터가_서비스로_전달된다() throws Exception {
        String channelId = "ch_session_test";
        StreamerSessionHistoryResponse response = new StreamerSessionHistoryResponse(
            Collections.emptyList(), 0, 10, 0, 0, false
        );

        given(sessionQueryService.getSessionHistory(channelId, 0, 10, true)).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/sessions", channelId)
                .param("page", "0")
                .param("size", "10")
                .param("paidPromotionOnly", "true"))
            .andExpect(status().isOk());
    }
}
