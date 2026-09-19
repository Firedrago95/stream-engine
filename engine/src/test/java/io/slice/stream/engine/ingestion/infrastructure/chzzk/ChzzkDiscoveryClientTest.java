package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive.Channel;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.Next;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.Page;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveStatusResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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

    private MockRestServiceServer mockServer;
    private ChzzkDiscoveryClient chzzkDiscoveryClient;

    @Mock
    private ExecutorService virtualExecutorService;

    private String baseUrl = "https://api.chzzk.naver.com";
    private String liveFetchUrl;
    private String liveDetailFetchUrl;
    private String liveStatusPollingUrl;

    @BeforeEach
    void setUp() {
        liveFetchUrl = "/service/v1/lives";
        liveDetailFetchUrl = "/service/v3/channels/{channelId}/live-detail";
        liveStatusPollingUrl = "/polling/v2/channels/{channelId}/live-status";

        Builder builder = RestClient.builder().baseUrl(baseUrl);
        mockServer = MockRestServiceServer.bindTo(builder)
            .build(new UnorderedRequestExpectationManager());
        Mockito.lenient().doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(virtualExecutorService).execute(any(Runnable.class));

        chzzkDiscoveryClient = new ChzzkDiscoveryClient(
            builder.build(),
            virtualExecutorService,
            liveFetchUrl,
            liveDetailFetchUrl,
            liveStatusPollingUrl
        );
    }

    @Test
    void 인기_라이브_스트림_목록을_가져와_경량_도메인_모델로_매핑한다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "침착맨의 일상", "https://thumb.com/1_{type}.jpg", "소통", "chatCh1", 5000, false, new Channel("ch1", "침착맨", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "게임 방송", "https://thumb.com/2_{type}.jpg", "게임", "chatCh2", 3000, false, new Channel("ch2", "게이머A", "imageUrl"));

        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live1, live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(2);
        StreamTarget target1 = result.get(0);
        assertThat(target1.channelId()).isEqualTo("ch1");
        assertThat(target1.channelName()).isEqualTo("침착맨");
        assertThat(target1.liveTitle()).isEqualTo("침착맨의 일상");
        assertThat(target1.concurrentUserCount()).isEqualTo(5000);
        assertThat(target1.categoryName()).isEqualTo("소통");
        assertThat(target1.chatChannelId()).isNull();
        assertThat(target1.startedAt()).isNull();

        StreamTarget target2 = result.get(1);
        assertThat(target2.channelId()).isEqualTo("ch2");
        assertThat(target2.channelName()).isEqualTo("게이머A");
        assertThat(target2.chatChannelId()).isNull();
        assertThat(target2.startedAt()).isNull();
    }

    @Test
    void 인기_라이브_스트림의_openDate가_있으면_startedAt으로_정상_변환된다() throws Exception {
        LocalDateTime openDateTime = LocalDateTime.of(2026, 9, 15, 10, 0, 0);
        Instant expectedInstant = openDateTime.toInstant(ZoneOffset.of("+09:00"));
        ChzzkLive live = new ChzzkLive(
            1001L, "침착맨의 일상", "https://thumb.com/1.jpg", "소통", "chatCh1", 5000, false, openDateTime, new Channel("ch1", "침착맨", "imageUrl")
        );

        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).startedAt()).isEqualTo(expectedInstant);
    }

    @Test
    void 성인방송은_필터링되어_결과에_포함되지_않는다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "일반 방송", "url", "게임", "chatCh1", 5000, false, new Channel("ch1", "스트리머A", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "성인 방송", "url", "게임", "chatCh2", 3000, true, new Channel("ch2", "스트리머B", "imageUrl"));

        ChzzkLiveResponse topLiveResponse = createMockResponse(List.of(live1, live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(topLiveResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).channelName()).isEqualTo("스트리머A");
    }

    @Test
    void 다음_페이지_커서가_존재하면_연속_페이지를_순회하여_전수_수집한다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "방송1", "url", "게임", "chatCh1", 5000, false, new Channel("ch1", "스트리머1", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "방송2", "url", "게임", "chatCh2", 4000, false, new Channel("ch2", "스트리머2", "imageUrl"));
        ChzzkLive live3 = new ChzzkLive(1003L, "방송3", "url", "게임", "chatCh3", 3000, false, new Channel("ch3", "스트리머3", "imageUrl"));
        ChzzkLive live4 = new ChzzkLive(1004L, "방송4", "url", "게임", "chatCh4", 2000, false, new Channel("ch4", "스트리머4", "imageUrl"));

        ChzzkLiveResponse page1Response = createMockResponse(List.of(live1), 4000L, 1002L);
        ChzzkLiveResponse page2Response = createMockResponse(List.of(live2), 3000L, 1003L);
        ChzzkLiveResponse page3Response = createMockResponse(List.of(live3), 2000L, 1004L);
        ChzzkLiveResponse page4Response = createMockResponse(List.of(live4), null, null);

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page1Response), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(buildTopLiveApiUri(50, 4000L, 1002L)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page2Response), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(buildTopLiveApiUri(50, 3000L, 1003L)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page3Response), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(buildTopLiveApiUri(50, 2000L, 1004L)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page4Response), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(4)
            .extracting(StreamTarget::channelName)
            .containsExactly("스트리머1", "스트리머2", "스트리머3", "스트리머4");
    }

    @Test
    void 마지막_방송의_시청자수가_10명_미만이면_10명_이상만_수집하고_다음_페이지_커서가_있어도_순회를_즉시_종료한다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "방송1", "url", "게임", "chatCh1", 100, false, new Channel("ch1", "스트리머1", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "방송2", "url", "게임", "chatCh2", 10, false, new Channel("ch2", "스트리머2", "imageUrl"));
        ChzzkLive live3 = new ChzzkLive(1003L, "방송3", "url", "게임", "chatCh3", 9, false, new Channel("ch3", "스트리머3", "imageUrl"));

        ChzzkLiveResponse pageResponse = createMockResponse(List.of(live1, live2, live3), 9L, 1003L);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(pageResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(2)
            .extracting(StreamTarget::concurrentUserCount)
            .containsExactly(100, 10);
    }

    @Test
    void 동일_페이지_커서가_반복되면_무한루프를_방지하기_위해_순회를_중단한다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "방송1", "url", "게임", "chatCh1", 100, false, new Channel("ch1", "스트리머1", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "방송2", "url", "게임", "chatCh2", 90, false, new Channel("ch2", "스트리머2", "imageUrl"));

        ChzzkLiveResponse page1Response = createMockResponse(List.of(live1), 90L, 1002L);
        ChzzkLiveResponse page2Response = createMockResponse(List.of(live2), 90L, 1002L);

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page1Response), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(buildTopLiveApiUri(50, 90L, 1002L)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(page2Response), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
        assertThat(result).hasSize(2);
    }

    @Test
    void 인기_라이브_조회_시_live_detail_API는_전혀_호출되지_않는다() throws Exception {
        ChzzkLive live1 = new ChzzkLive(1001L, "방송1", "url", "게임", "chatCh1", 500, false, new Channel("ch1", "스트리머1", "imageUrl"));
        ChzzkLive live2 = new ChzzkLive(1002L, "방송2", "url", "게임", "chatCh2", 400, false, new Channel("ch2", "스트리머2", "imageUrl"));

        ChzzkLiveResponse response = createMockResponse(List.of(live1, live2), null, null);
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        chzzkDiscoveryClient.fetchTopLiveStreams(200);

        mockServer.verify();
    }

    @Test
    void 순위_밖_방송_상세_조회_시에는_live_detail_API를_호출한다() throws Exception {
        ChzzkLiveDetailResponse detailResponse = new ChzzkLiveDetailResponse(
            new ChzzkLiveDetailResponse.Content(
                "OPEN",
                "chatCh1",
                LocalDateTime.now(),
                "순위 밖 방송",
                "게임",
                50,
                1001L,
                new Channel("ch1", "스트리머1", "imageUrl")
            )
        );
        mockServer.expect(requestTo(buildLiveDetailApiUri("ch1")))
            .andRespond(withSuccess(objectMapper.writeValueAsString(detailResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchLiveStreams(Set.of("ch1"));

        mockServer.verify();
        assertThat(result).hasSize(1);
        StreamTarget target = result.get(0);
        assertThat(target.channelName()).isEqualTo("스트리머1");
        assertThat(target.chatChannelId()).isEqualTo("chatCh1");
        assertThat(target.startedAt()).isNotNull();
    }

    @Test
    void API_응답_데이터가_비어있을_경우_빈_목록을_반환한다() throws Exception {
        ChzzkLiveResponse emptyResponse = createMockResponse(List.of(), null, null);

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(emptyResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(5);

        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_응답의_Content_내부_데이터가_null일_경우_빈_목록을_반환한다() throws Exception {
        ChzzkLiveResponse nullDataResponse = new ChzzkLiveResponse(new Content(0, null, null));

        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withSuccess(objectMapper.writeValueAsString(nullDataResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchTopLiveStreams(5);

        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    void API_호출이_실패하면_IngestionException을_던진다() {
        mockServer.expect(requestTo(buildTopLiveApiUri(50, null, null)))
            .andRespond(withServerError());

        assertThatThrownBy(() -> chzzkDiscoveryClient.fetchTopLiveStreams(5))
            .isInstanceOf(IngestionException.class)
            .hasMessageContaining("API 호출 실패")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR);
    }

    @Test
    void 웹소켓_연결을_위해_경량_상태_조회_후_chatChannelId를_채워_반환한다() throws Exception {
        StreamTarget target = new StreamTarget("ch1", "스트리머1", null, 1001L, "방송 제목", 100, "thumb.jpg", "소통", Instant.EPOCH);
        ChzzkLiveStatusResponse statusResponse = new ChzzkLiveStatusResponse(
            new ChzzkLiveStatusResponse.Content(
                "방송 제목",
                "OPEN",
                100,
                0,
                LocalDateTime.now(),
                "chatCh1",
                "소통",
                "ch1"
            )
        );

        mockServer.expect(requestTo(buildLiveStatusApiUri("ch1")))
            .andRespond(withSuccess(objectMapper.writeValueAsString(statusResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchLiveStreamsForChat(Set.of(target));

        mockServer.verify();
        assertThat(result).hasSize(1);
        StreamTarget readyTarget = result.get(0);
        assertThat(readyTarget.channelId()).isEqualTo("ch1");
        assertThat(readyTarget.channelName()).isEqualTo("스트리머1");
        assertThat(readyTarget.liveId()).isEqualTo(1001L);
        assertThat(readyTarget.chatChannelId()).isEqualTo("chatCh1");
    }

    @Test
    void 경량_상태_조회_결과_CLOSE_상태이면_웹소켓_대상에서_제외한다() throws Exception {
        StreamTarget target = new StreamTarget("ch1", "스트리머1", null, 1001L, "방송 제목", 100, "thumb.jpg", "소통", Instant.EPOCH);
        ChzzkLiveStatusResponse statusResponse = new ChzzkLiveStatusResponse(
            new ChzzkLiveStatusResponse.Content(
                "방송 제목",
                "CLOSE",
                0,
                500,
                LocalDateTime.now(),
                "chatCh1",
                "소통",
                "ch1"
            )
        );

        mockServer.expect(requestTo(buildLiveStatusApiUri("ch1")))
            .andRespond(withSuccess(objectMapper.writeValueAsString(statusResponse), MediaType.APPLICATION_JSON));

        List<StreamTarget> result = chzzkDiscoveryClient.fetchLiveStreamsForChat(Set.of(target));

        mockServer.verify();
        assertThat(result).isEmpty();
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

    private String buildLiveStatusApiUri(String channelId) {
        return UriComponentsBuilder.fromUriString(baseUrl + liveStatusPollingUrl)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
