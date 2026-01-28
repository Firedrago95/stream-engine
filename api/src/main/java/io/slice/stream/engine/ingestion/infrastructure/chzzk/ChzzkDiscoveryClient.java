package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.global.error.ErrorCode;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.error.IngestionException;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkLiveResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

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
        ChzzkLiveResponse topLivesResponse = callChzzkApi(topLiveUri);

        return Optional.ofNullable(topLivesResponse)
            .map(r -> r.content().data())
            .orElse(Collections.emptyList())
            .stream()
            .map(topLive -> {
                String channelId = topLive.channel().channelId();
                ChzzkLiveResponse.Content.ChzzkLive detailLive = fetchLiveDetail(channelId);
                return new StreamTarget(
                    topLive.channel().channelId(),
                    topLive.channel().channelName(),
                    detailLive.chatChannelId(),
                    topLive.liveId(),
                    topLive.liveTitle(),
                    topLive.concurrentUserCount()
                );
            }).toList();
    }

    private String buildTopLiveApiUri(int limit) {
        return UriComponentsBuilder.fromPath(liveFetch)
            .queryParam("sort", "POPULAR")
            .queryParam("size", limit)
            .toUriString();
    }

    private ChzzkLiveResponse.Content.ChzzkLive fetchLiveDetail(String channelId) {
        String uri = UriComponentsBuilder.fromPath(liveDetailFetch)
            .buildAndExpand(channelId)
            .toUriString();
        return callChzzkApi(uri).content().data().get(0);
    }

    private ChzzkLiveResponse callChzzkApi(String url) {
        try {
            return restClient.get()
                .uri(url)
                .retrieve()
                .body(ChzzkLiveResponse.class);
        } catch (RestClientException e) {
            throw new IngestionException(ErrorCode.STREAM_PROVIDER_CLIENT_ERROR, "치지직 API 호출에 실패했습니다.");
        }
    }
}
