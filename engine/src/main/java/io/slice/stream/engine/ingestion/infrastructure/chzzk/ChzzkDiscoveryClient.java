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
import java.util.HashSet;
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
        List<ChzzkLive> topLives = fetchTopLives();

        if (topLives.isEmpty()) {
            return Collections.emptyList();
        }
        return topLives.stream()
            .map(this::convertToStreamTarget)
            .toList();
    }

    @Override
    public List<StreamTarget> fetchLiveStreams(Set<String> channelIds) {
        List<CompletableFuture<StreamTarget>> fetchResults = channelIds.stream()
            .map(channelId -> CompletableFuture.supplyAsync(() -> {
                try {
                    rateLimiter.acquire();
                    Content content = fetchLiveDetail(channelId);
                    if (content != null && "OPEN".equals(content.status())) {
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

    private StreamTarget convertToStreamTarget(ChzzkLive live) {
        Instant startedAt = live.openDate() != null
            ? live.openDate().toInstant(ZoneOffset.of("+09:00"))
            : null;
        return new StreamTarget(
            live.channel().channelId(),
            live.channel().channelName(),
            null,
            live.liveId(),
            live.liveTitle(),
            live.concurrentUserCount(),
            live.channel().channelImageUrl(),
            live.liveCategoryValue(),
            startedAt
        );
    }

    private List<ChzzkLive> fetchTopLives() {
        List<ChzzkLive> collectedLives = new ArrayList<>();

        Long nextConcurrentUserCount = null;
        Long nextLiveId = null;
        int pageCount = 0;
        Set<String> visitedCursors = new HashSet<>();

        log.info("[Chzzk API] TopLive 랭킹 전수 조사 시작");

        while (true) {
            pageCount++;
            String topLiveUri = buildTopLiveApiUri(50, nextConcurrentUserCount, nextLiveId);
            ChzzkLiveResponse topLiveResponse = callTopLivesApi(topLiveUri);

            if (topLiveResponse == null || topLiveResponse.content() == null ||
                topLiveResponse.content().data() == null || topLiveResponse.content().data().isEmpty()) {
                break;
            }

            List<ChzzkLive> data = topLiveResponse.content().data();

            List<ChzzkLive> validLives = data.stream()
                .filter(live -> !live.adult())
                .toList();

            collectedLives.addAll(validLives);

            ChzzkLive lastLive = data.get(data.size() - 1);
            if (lastLive.concurrentUserCount() == 0) {
                break;
            }

            Page page = topLiveResponse.content().page();
            if (page == null || page.next() == null) {
                break;
            }

            nextConcurrentUserCount = page.next().concurrentUserCount();
            nextLiveId = page.next().liveId();

            if (nextConcurrentUserCount == null || nextLiveId == null) {
                break;
            }

            String cursorKey = nextConcurrentUserCount + ":" + nextLiveId;
            if (!visitedCursors.add(cursorKey)) {
                log.warn("[Chzzk API] 동일 커서 반복 감지로 순회를 중단합니다: {}", cursorKey);
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[Chzzk API] TopLive 랭킹 전수 수집 완료 (수집: {}/건, 순회 페이지: {})", collectedLives.size(), pageCount);
        return collectedLives;
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
