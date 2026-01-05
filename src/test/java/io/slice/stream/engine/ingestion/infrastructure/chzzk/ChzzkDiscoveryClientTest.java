package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive.Channel;
import java.util.List;
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
class ChzzkDiscoveryClientTest {

    private ChzzkDiscoveryClient chzzkDiscoveryClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        String baseUrl = "https://api.chzzk.naver.com";
        String liveFetchUrl = "/service/v1/lives";

        Builder builder = RestClient.builder().baseUrl(baseUrl);

        mockServer = MockRestServiceServer.bindTo(builder).build();

        chzzkDiscoveryClient = new ChzzkDiscoveryClient(builder.build(), liveFetchUrl);
    }

    @Test
    void 인기_라이브_스트림_목록을_가져와_도메인_모델로_매핑한다() throws Exception {
        // Given
        int limit = 5;
        ChzzkLiveResponse mockResponse = createMockResponse(List.of(
            new ChzzkLive(1001L, "침착맨의 일상", 5000, new Channel("ch1", "침착맨")),
            new ChzzkLive(1002L, "게임 방송", 3000, new Channel("ch2", "게이머A"))
        ));

        mockServer.expect(anything())
            .andRespond(withSuccess(objectMapper.writeValueAsString(mockResponse), MediaType.APPLICATION_JSON));

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).liveTitle()).isEqualTo("침착맨의 일상");
        assertThat(result.get(0).channelName()).isEqualTo("침착맨");
    }

    @Test
    void API_응답_데이터가_비어있을_경우_빈_목록을_반환한다() throws Exception {
        // Given
        ChzzkLiveResponse emptyResponse = createMockResponse(List.of());

        mockServer.expect(anything())
            .andRespond(withSuccess(objectMapper.writeValueAsString(emptyResponse), MediaType.APPLICATION_JSON));

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(5);

        // Then
        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_응답의_Content_내부_데이터가_null일_경우_빈_목록을_반환한다() throws Exception {
        // Given
        ChzzkLiveResponse nullDataResponse = createMockResponse(null);

        mockServer.expect(anything())
            .andRespond(withSuccess(objectMapper.writeValueAsString(nullDataResponse), MediaType.APPLICATION_JSON));

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(5);

        // Then
        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_호출이_실패하면_IngestionException을_던진다() {
        // Given
        mockServer.expect(anything())
            .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> chzzkDiscoveryClient.fetchTopLiveStreams(5))
            .isInstanceOf(IngestionException.class)
            .hasMessage("치지직 API 호출에 실패했습니다.")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR);
    }

    private ChzzkLiveResponse createMockResponse(List<ChzzkLive> data) {
        return new ChzzkLiveResponse(new Content(data));
    }
}
