package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
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
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.Next;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkDiscoveryClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChzzkDiscoveryClient chzzkDiscoveryClient;
    private MockRestServiceServer mockServer;

    @Mock
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
        Mockito.lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(virtualExecutorService).execute(any(Runnable.class));

        chzzkDiscoveryClient = new ChzzkDiscoveryClient(builder.build(), virtualExecutorService, liveFetchUrl, liveDetailFetchUrl);
    }

    @Test
    void 인기_라이브_스트림_목록을_가져와_도메인_모델로_매핑한다() throws Exception {
        // Given
        int limit = 2; // 목표 수량
        // DTO에 adult 필드(false) 추가
        ChzzkLive live1 = new ChzzkLive(1001L, "침착맨의 일상", "https://thumb.com/1_{type}.jpg", "소통", "chatCh1", 5000, false, new Channel("ch1", "침착맨", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "게임 방송", "https://thumb.com/2_{type}.jpg", "게임", "chatCh2", 3000, false, new Channel("ch2", "게이머A", "imageUrl"));

        // 클라이언트는 내부적으로 무조건 size=50으로 요청함
        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live1, live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

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
    void 성인방송은_필터링되어_결과에_포함되지_않는다() throws Exception {
        // Given
        int limit = 5;
        // live2를 성인방송(adult = true)으로 설정
        ChzzkLive live1 = new ChzzkLive(1001L, "일반 방송", "url", "게임", "chatCh1", 5000, false, new Channel("ch1", "스트리머A", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "성인 방송", "url", "게임", "chatCh2", 3000, true, new Channel("ch2", "스트리머B", "imageUrl"));

        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live1, live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

        mockDetailApi("ch1", "chatCh1");

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).channelName()).isEqualTo("스트리머A");
    }

    @Test
    void 목표_수량이_한_페이지를_초과하면_커서를_이용해_다음_페이지를_조회한다() throws Exception {
        // Given
        int limit = 2; // 테스트를 위해 limit을 2로 잡고, 페이지당 1개씩 리턴한다고 가정

        ChzzkLive live1 = new ChzzkLive(1001L, "방송1", "url", "게임", "chatCh1", 5000, false, new Channel("ch1", "스트리머1", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "방송2", "url", "게임", "chatCh2", 3000, false, new Channel("ch2", "스트리머2", "imageUrl"));

        // 첫 번째 페이지 응답 (다음 커서 존재)
        ChzzkLiveResponse page1Response = createMockResponse(List.of(live1), 3000L, 1002L);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page1Response), MediaType.APPLICATION_JSON));

        // 두 번째 페이지 응답 (커서 없음)
        ChzzkLiveResponse page2Response = createMockResponse(List.of(live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, 3000L, 1002L)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page2Response), MediaType.APPLICATION_JSON));

        mockDetailApi("ch1", "chatCh1");
        mockDetailApi("ch2", "chatCh2");

        // When
        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(limit);

        // Then
        mockServer.verify(); // 두 번의 API 호출이 모두 발생했는지 검증
        assertThat(result).hasSize(2)
            .extracting("channelName")
            .containsExactly("스트리머1", "스트리머2");
    }

    @Test
    void API_응답_데이터가_비어있을_경우_빈_목록을_반환한다() throws Exception {
        // Given
        ChzzkLiveResponse emptyResponse = createMockResponse(List.of(), null, null);

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
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
        ChzzkLiveResponse nullDataResponse = new ChzzkLiveResponse(new Content(0, null, null));

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
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
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> chzzkDiscoveryClient.fetchTopLiveStreams(5))
            .isInstanceOf(IngestionException.class)
            .hasMessageContaining("API 호출 실패")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR);
    }

    private void mockDetailApi(String channelId, String chatChannelId) throws Exception {
        ChzzkLiveDetailResponse detailResponse = new ChzzkLiveDetailResponse(
            new ChzzkLiveDetailResponse.Content("OPEN", chatChannelId, LocalDateTime.now())
        );
        mockServer.expect(requestTo(buildLiveDetailApiUri(channelId)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(detailResponse), MediaType.APPLICATION_JSON));
    }

    private ChzzkLiveResponse createMockResponse(List<ChzzkLive> data, Long nextViewers, Long nextLiveId) {
        Next next = (nextViewers != null && nextLiveId != null) ? new Next(nextViewers, nextLiveId) : null;
        Page page = new Page(next);
        return new ChzzkLiveResponse(new Content(data.size(), page, data));
    }

    private String buildTopLiveApiUri(int size, Long concurrentUserCount, Long liveId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + liveFetchUrl)
            .queryParam("sort", "POPULAR")
            .queryParam("size", size);

        if (concurrentUserCount != null && liveId != null) {
            builder.queryParam("concurrentUserCount", concurrentUserCount)
                .queryParam("liveId", liveId);
        }

        return builder.toUriString();
    }

    private String buildLiveDetailApiUri(String channelId) {
        return UriComponentsBuilder.fromUriString(baseUrl + liveDetailFetchUrl)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
