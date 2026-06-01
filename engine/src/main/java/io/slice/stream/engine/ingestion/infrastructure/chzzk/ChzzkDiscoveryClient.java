package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import com.google.common.util.concurrent.RateLimiter;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveDetailResponse.Content;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.ChzzkLive;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse.Content.Page;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@Profile("!local")
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

    @Override
    @Retryable(
        includes = RestClientException.class,
        maxRetries = 2,
        delay = 400
    )
    public List<StreamTarget> fetchLiveStreams(Set<String> channelIds) {
        List<CompletableFuture<StreamTarget>> fetchResults = channelIds.stream()
            .map(channelId -> CompletableFuture.supplyAsync(() -> {
                try {
                    rateLimiter.acquire();
                    Content content = fetchLiveDetail(channelId);
                    if (content != null && content.status().equals("OPEN")) {
                        return convertToStreamTarget(content);
                    }
                    return null;
                } catch (Exception e) {
                    log.warn("[Chzzk API] 순위 밖 방송 상세 조회 중 에러 발생. channelId: {}", channelId);
                    return null;
                }
            }, virtualThreadExecutor))
            .toList();

        return fetchResults.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    private StreamTarget convertToStreamTarget(Content detailContent) {
        Instant startedAt = detailContent.openDate().toInstant(ZoneOffset.of("+09:00"));
        return new StreamTarget(
            detailContent.channel().channelId(),
            detailContent.channel().channelName(),
            detailContent.chatChannelId(),
            detailContent.liveId(),
            detailContent.liveTitle(),
            detailContent.concurrentUserCount(),
            detailContent.channel().channelImageUrl(),
            detailContent.liveCategoryValue(),
            startedAt
        );
    }

    private List<ChzzkLive> fetchTopLives(int limit) {
        List<ChzzkLive> collectedLives = new ArrayList<>();

        Long nextConcurrentUserCount = null;
        Long nextLiveId = null;

        log.info("[Chzzk API] TopLive 랭킹 조회 시작 (목표 수량: {})", limit);

        while (collectedLives.size() < limit) {
            String topLiveUri = buildTopLiveApiUri(50, nextConcurrentUserCount, nextLiveId);
            ChzzkLiveResponse topLiveResponse = callTopLivesApi(topLiveUri);

            if (topLiveResponse == null || topLiveResponse.content() == null ||
                topLiveResponse.content().data() == null || topLiveResponse.content().data().isEmpty()) {
                break;
            }

            // 연령제한 방송 필터링
            List<ChzzkLive> validLives = topLiveResponse.content().data().stream()
                .filter(live -> !live.adult())
                .toList();

            collectedLives.addAll(validLives);

            // 페이지 정보가 있는 경우 갱신 (다음 페이지 조회 필요시 사용)
            Page page = topLiveResponse.content().page();
            if (page != null && page.next() != null) {
                nextConcurrentUserCount = page.next().concurrentUserCount();
                nextLiveId = page.next().liveId();
            } else {
                break;
            }
        }

        List<ChzzkLive> result = collectedLives.size() > limit
            ? collectedLives.subList(0, limit)
            : collectedLives;
        log.info("[Chzzk  API] TopLive 랭킹 수집 완료 (수집: {}/건)", result.size());
        return result;
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
            if (log.isDebugEnabled()) {
                log.debug("[Chzzk API] LiveDetail 요청 URL: {}", url);
            }
            return restClient.get()
                .uri(url)
                .retrieve()
                .body(ChzzkLiveDetailResponse.class);
        } catch (RestClientException e) {
            log.error("[Chzzk API Error] LiveDetail 호출 실패. URL: {}", url, e);
            throw new IngestionException(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR, "치지직 API 호출에 실패했습니다.");
        }
    }

    private String buildTopLiveApiUri(int size, Long concurrentUserCount, Long liveId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(liveFetch)
            .queryParam("sort", "POPULAR")
            .queryParam("size", size);

        if (concurrentUserCount != null && liveId != null) {
            builder.queryParam("concurrentUserCount", concurrentUserCount)
                .queryParam("liveId", liveId);
        }

        return builder.toUriString();
    }

    private String buildLiveDetailApiUri(String channelId) {
        return UriComponentsBuilder.fromPath(liveDetailFetch)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
