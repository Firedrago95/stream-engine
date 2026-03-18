package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import com.google.common.util.concurrent.RateLimiter;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
    private final ExecutorService virtualThreadExecutor;
    private final RateLimiter rateLimiter = RateLimiter.create(10.0);

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
        List<ChzzkLive> topLives = fetchTopLives(limit);

        if (topLives.isEmpty()) {
            return Collections.emptyList();
        }
        return fetchAllLiveDetailsConcurrently(topLives);
    }

    private List<ChzzkLive> fetchTopLives(int limit) {
        String topLiveUri = buildTopLiveApiUri(limit);
        ChzzkLiveResponse topLivesResponse = callTopLivesApi(topLiveUri);

        return Optional.ofNullable(topLivesResponse)
            .map(r -> r.content().data())
            .orElse(Collections.emptyList());
    }

    private List<StreamTarget> fetchAllLiveDetailsConcurrently(List<ChzzkLive> lives) {
        log.info("[Chzzk API] {}개의 방송 상세 정보(LiveDetail) 병렬 조회 시작", lives.size());
        List<CompletableFuture<StreamTarget>> futures = lives.stream()
            .map(live -> CompletableFuture.supplyAsync(() -> {
                rateLimiter.acquire();
                return convertToStreamTarget(live);
            }, virtualThreadExecutor)).toList();

        try {
            List<StreamTarget> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

            log.info("[Chzzk API] 방송 상세 정보 조회 완료 (성공: {}/건)", results.size());
            return results;
        } catch (Exception e) {
            log.warn("상세 정보 조회 전체 대기 중 작업이 중단되었습니다.",e);
            return Collections.emptyList();
        }
    }

    private StreamTarget convertToStreamTarget(ChzzkLive topLive) {
        String channelId = topLive.channel().channelId();
        try {
            log.debug("채널 id로 상세 조회 시작: {}", channelId);
            ChzzkLiveDetailResponse.Content detailContent = fetchLiveDetail(channelId);
            Instant startedAt = detailContent.openDate().toInstant(ZoneOffset.of("+09:00"));
            return new StreamTarget(
                channelId,
                topLive.channel().channelName(),
                detailContent.chatChannelId(),
                topLive.liveId(),
                topLive.liveTitle(),
                topLive.concurrentUserCount(),
                topLive.channel().channelImageUrl(),
                topLive.liveCategoryValue(),
                startedAt
            );
        } catch (Exception e) {
            log.warn("방송 상세 정보 조회 중 에러 발생. channelName: {}", topLive.channel().channelName());
            return null;
        }
    }

    private ChzzkLiveDetailResponse.Content fetchLiveDetail(String channelId) {
        String uri = buildLiveDetailApiUri(channelId);
        ChzzkLiveDetailResponse response = callLiveDetailApi(uri);
        return response.content();
    }

    private ChzzkLiveResponse callTopLivesApi(String url) {
        try {
            log.debug("[Chzzk API] TopLive 요청 URL: {}", url);
            return restClient.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    log.error("TopLive API Error - Body: {}", body.replaceAll("[\r\n]", " "));
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

    private String buildTopLiveApiUri(int limit) {
        return UriComponentsBuilder.fromPath(liveFetch)
            .queryParam("sort", "POPULAR")
            .queryParam("size", limit)
            .toUriString();
    }

    private String buildLiveDetailApiUri(String channelId) {
        return UriComponentsBuilder.fromPath(liveDetailFetch)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
