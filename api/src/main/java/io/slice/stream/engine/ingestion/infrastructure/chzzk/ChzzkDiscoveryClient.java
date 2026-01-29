package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChzzkDiscoveryClient implements StreamDiscoveryClient {

    private final RestClient restClient;

    @Value("${chzzk.api.live-fetch}")
    private final String liveFetch;

    @Value("${chzzk.api.live-detail-fetch}")
    private final String liveDetailFetch;

    @Override
    @Retryable(
        includes = RestClientException.class,
        maxRetries = 2,
        delay = 400
    )
    public List<StreamTarget> fetchTopLiveStreams(int limit) {
        String topLiveUri = buildTopLiveApiUri(limit);
        ChzzkLiveResponse topLivesResponse = callTopLivesApi(topLiveUri);

        return Optional.ofNullable(topLivesResponse)
            .map(r -> r.content().data())
            .orElse(Collections.emptyList())
            .stream()
            .map(this::convertToStreamTarget)
            .filter(Objects::nonNull)
            .toList();
    }

    private StreamTarget convertToStreamTarget(ChzzkLive topLive) {
        String channelId = topLive.channel().channelId();
        try {
            log.debug("채널 id로 상세 조회 시작: {}", channelId);
            ChzzkLiveDetailResponse.Content detailContent = fetchLiveDetail(channelId);
            return new StreamTarget(
                topLive.channel().channelId(),
                topLive.channel().channelName(),
                detailContent.chatChannelId(),
                topLive.liveId(),
                topLive.liveTitle(),
                topLive.concurrentUserCount()
            );
        } catch (Exception e) {
            log.warn("방송 상세 정보 조회 중 에러 발생. channelName: {}", topLive.channel().channelName());
            return null;
        }
    }

    private String buildTopLiveApiUri(int limit) {
        return UriComponentsBuilder.fromPath(liveFetch)
            .queryParam("sort", "POPULAR")
            .queryParam("size", limit)
            .toUriString();
    }

    private ChzzkLiveDetailResponse.Content fetchLiveDetail(String channelId) {
        String uri = UriComponentsBuilder.fromPath(liveDetailFetch)
            .buildAndExpand(channelId)
            .toUriString();
        ChzzkLiveDetailResponse response = callLiveDetailApi(uri);
        return response.content();
    }

    private ChzzkLiveResponse callTopLivesApi(String url) {
        try {
            log.info("[Chzzk API] TopLive 요청 URL: {}", url);
            return restClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    log.error("TopLive API Error - Body: {}", body);
                    throw new IngestionException(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR, "API 호출 실패: " + body);
                })
                .body(ChzzkLiveResponse.class);
        } catch (RestClientException e) {
            log.error("[Chzzk API Error] TopLive 호출 실패. URL: {}", url, e);
            throw new IngestionException(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR, "치지직 API 호출에 실패했습니다.");
        }
    }

    private ChzzkLiveDetailResponse callLiveDetailApi(String url) {
        try {
            log.info("[Chzzk API] LiveDetail 요청 URL: {}", url);
            return restClient.get()
                .uri(url)
                .retrieve()
                .body(ChzzkLiveDetailResponse.class);
        } catch (RestClientException e) {
            log.error("[Chzzk API Error] LiveDetail 호출 실패. URL: {}", url, e);
            throw new IngestionException(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR, "치지직 API 호출에 실패했습니다.");
        }
    }
}
