package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
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

    @Value("${url.live-fetch}")
    private final String liveFetch;

    @Override
    @Retryable(
        includes = RestClientException.class,
        maxRetries = 2,
        delay = 400
    )
    public List<StreamTarget> fetchTopLiveStreams(int limit) {
        String uri = buildApiUri(limit);

        ChzzkLiveResponse response = callChzzkApi(uri);

        return toStreamTargets(response);
    }

    private String buildApiUri(int limit) {
        return UriComponentsBuilder.fromPath(liveFetch)
            .queryParam("sort", "POPULAR")
            .queryParam("size", limit)
            .toUriString();
    }

    private ChzzkLiveResponse callChzzkApi(String url) {
        return restClient.get()
            .uri(url)
            .retrieve()
            .body(ChzzkLiveResponse.class);
    }

    private static List<StreamTarget> toStreamTargets(ChzzkLiveResponse response) {
        return Optional.ofNullable(response)
            .map(r -> r.content().data())
            .orElse(Collections.emptyList())
            .stream()
            .map(it -> new StreamTarget(
                it.liveId(),
                it.liveTitle(),
                it.channel().channelId(),
                it.channel().channelName(),
                it.concurrentUserCount()
            )).toList();
    }
}
