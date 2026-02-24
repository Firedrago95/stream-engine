package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive.Channel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkDiscoveryClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChzzkDiscoveryClient chzzkDiscoveryClient;
    private MockRestServiceServer mockServer;
    private ExecutorService virtualExecutorService;

    private String baseUrl = "https://api.chzzk.naver.com";
    private String liveFetchUrl;
    private String liveDetailFetchUrl;

    @BeforeEach
    void setUp() {
        liveFetchUrl = "/service/v1/lives";
        liveDetailFetchUrl = "/service/v2/channels/{channelId}/live-detail";

        Builder builder = RestClient.builder().baseUrl(baseUrl);
        mockServer = MockRestServiceServer.bindTo(builder)
            .build(new UnorderedRequestExpectationManager());
        virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();
        chzzkDiscoveryClient = new ChzzkDiscoveryClient(builder.build(), virtualExecutorService, liveFetchUrl, liveDetailFetchUrl);
    }

    @Test
    void 인기_라이브_스트림_목록을_가져와_도메인_모델로_매핑한다() throws Exception {
        // Given
        int limit = 5;
        ChzzkLive live1 = new ChzzkLive(1001L, "침착맨의 일상", "https://thumb.com/1_{type}.jpg", "소통", "5000", 5000, new Channel("ch1", "침착맨"));
        ChzzkLive live2 = new ChzzkLive(1002L, "게임 방송", "https://thumb.com/2_{type}.jpg", "게임", "3000", 3000, new Channel("ch2", "게이머A"));

        // 1. live-fetch (상위 라이브 목록 조회) 응답 Mocking
        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live1, live2));

        mockServer.expect(requestTo(buildTopLiveApiUri(limit)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

        // 2. 상세 정보 응답 Mocking
        mockDetailApi("ch1", "chatCh1");
        mockDetailApi("ch2", "chatCh2");

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify();
        assertThat(result).hasSize(2)
            .extracting("channelName", "liveTitle", "chatChannelId")
            .containsExactlyInAnyOrder(
                tuple("침착맨", "침착맨의 일상", "chatCh1"),
                tuple("게이머A", "게임 방송", "chatCh2")
            );
    }

    @Test
    void API_응답_데이터가_비어있을_경우_빈_목록을_반환한다() throws Exception {
        // Given
        int limit = 5;
        ChzzkLiveResponse emptyResponse = createMockResponse(List.of());

        mockServer.expect(requestTo(buildTopLiveApiUri(limit)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(emptyResponse), MediaType.APPLICATION_JSON));

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_응답의_Content_내부_데이터가_null일_경우_빈_목록을_반환한다() throws Exception {
        // Given
        int limit = 5;
        ChzzkLiveResponse nullDataResponse = createMockResponse(null);

        mockServer.expect(requestTo(buildTopLiveApiUri(limit)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(nullDataResponse), MediaType.APPLICATION_JSON));

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_호출이_실패하면_IngestionException을_던진다() {
        // Given
        int limit = 5;
        mockServer.expect(requestTo(buildTopLiveApiUri(limit)))
            .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> chzzkDiscoveryClient.fetchTopLiveStreams(limit))
            .isInstanceOf(IngestionException.class)
            .hasMessage("API 호출 실패: ")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR);
    }

    private void mockDetailApi(String channelId, String chatChannelId) throws Exception {
        ChzzkLiveDetailResponse detailResponse = new ChzzkLiveDetailResponse(
            new ChzzkLiveDetailResponse.Content("OPEN", chatChannelId)
        );
        mockServer.expect(requestTo(buildLiveDetailApiUri(channelId)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(detailResponse), MediaType.APPLICATION_JSON));
    }

    private ChzzkLiveResponse createMockResponse(List<ChzzkLive> data) {
        return new ChzzkLiveResponse(new Content(data));
    }

    private String buildTopLiveApiUri(int limit) {
        return UriComponentsBuilder.fromUriString(baseUrl + liveFetchUrl)
            .queryParam("sort", "POPULAR")
            .queryParam("size", limit)
            .toUriString();
    }

    private String buildLiveDetailApiUri(String channelId) {
        return UriComponentsBuilder.fromUriString(baseUrl + liveDetailFetchUrl)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
