package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkChannelResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkChannelClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer mockServer;
    private ChzzkChannelClient channelClient;

    private final String baseUrl = "https://api.chzzk.naver.com";
    private final String channelFetchUrl = "/service/v1/channels/{channelId}";

    @BeforeEach
    void setUp() {
        Builder builder = RestClient.builder().baseUrl(baseUrl);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        channelClient = new ChzzkChannelClient(builder.build(), channelFetchUrl, 5.0);
    }

    @Test
    void 채널_조회에_성공하면_팔로워_수를_반환한다() throws Exception {
        String channelId = "ch1";
        ChzzkChannelResponse response = new ChzzkChannelResponse(
            new ChzzkChannelResponse.Content(
                channelId, "스트리머1", "thumb.jpg", 15000, true, false
            )
        );

        mockServer.expect(requestTo(baseUrl + "/service/v1/channels/" + channelId))
            .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        Optional<Integer> result = channelClient.fetchFollowerCount(channelId);

        mockServer.verify();
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(15000);
    }

    @Test
    void API_호출_실패시_빈_Optional을_반환한다() {
        String channelId = "ch_error";
        mockServer.expect(requestTo(baseUrl + "/service/v1/channels/" + channelId))
            .andRespond(withServerError());

        Optional<Integer> result = channelClient.fetchFollowerCount(channelId);

        mockServer.verify();
        assertThat(result).isEmpty();
    }
}
