package io.slice.stream.apiserver.streamer.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.slice.stream.apiserver.streamer.application.StreamerCalendarQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerLeaderboardQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerProfileQueryService;
import io.slice.stream.apiserver.streamer.application.StreamerSessionQueryService;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamerQueryController controller = new StreamerQueryController(
            leaderboardQueryService,
            profileQueryService,
            calendarQueryService,
            sessionQueryService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
}
