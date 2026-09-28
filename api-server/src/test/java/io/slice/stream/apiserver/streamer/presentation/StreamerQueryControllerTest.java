package io.slice.stream.apiserver.streamer.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.streamer.application.StreamerCalendarQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerProfileQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSessionQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSimilarityQueryService;
import io.slice.stream.apiserver.streamer.domain.model.SimilarityStatus;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSimilarityResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSimilarityResponse.SimilarChannelDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerQueryControllerTest {

    @Mock
    private StreamerLeaderboardQueryService leaderboardQueryService;

    @Mock
    private StreamerProfileQueryService profileQueryService;

    @Mock
    private StreamerCalendarQueryService calendarQueryService;

    @Mock
    private StreamerSessionQueryService sessionQueryService;

    @Mock
    private StreamerSimilarityQueryService similarityQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamerQueryController controller = new StreamerQueryController(
            leaderboardQueryService,
            profileQueryService,
            calendarQueryService,
            sessionQueryService,
            similarityQueryService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 스트리머_유사_채널_조회_API가_성공적으로_응답한다() throws Exception {
        String channelId = "ch_test_channel";
        LocalDate date = LocalDate.of(2026, 9, 28);
        SimilarChannelDto item = new SimilarChannelDto(
            1, "ch_similar", "유사스트리머", "https://img.png", "Just Chatting", 15.4, 150, 1000
        );
        StreamerSimilarityResponse response = new StreamerSimilarityResponse(
            channelId, SimilarityStatus.NORMAL, date, List.of(item)
        );

        given(similarityQueryService.getSimilarities(channelId)).willReturn(response);

        mockMvc.perform(get("/api/v1/streamers/{channelId}/similarities", channelId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.streamId").value(channelId))
            .andExpect(jsonPath("$.status").value("NORMAL"))
            .andExpect(jsonPath("$.calculatedDate").value("2026-09-28"))
            .andExpect(jsonPath("$.items[0].rank").value(1))
            .andExpect(jsonPath("$.items[0].channelId").value("ch_similar"))
            .andExpect(jsonPath("$.items[0].streamerName").value("유사스트리머"))
            .andExpect(jsonPath("$.items[0].similarityPercent").value(15.4));
    }
}
