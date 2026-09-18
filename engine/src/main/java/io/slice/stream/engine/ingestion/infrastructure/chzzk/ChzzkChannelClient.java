package io.slice.stream.engine.ingestion.infrastructure.chzzk;

import com.google.common.util.concurrent.RateLimiter;
import io.slice.stream.engine.ingestion.infrastructure.chzzk.dto.response.ChzzkChannelResponse;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class ChzzkChannelClient {

    private final RestClient restClient;
    private final String channelFetchUrl;
    private final RateLimiter rateLimiter;

    public ChzzkChannelClient(
        RestClient restClient,
        @Value("${chzzk.api.channel-fetch:/service/v1/channels/{channelId}}") String channelFetchUrl,
        @Value("${chzzk.collector.follower.tps:5.0}") double tps
    ) {
        this.restClient = restClient;
        this.channelFetchUrl = channelFetchUrl;
        this.rateLimiter = RateLimiter.create(tps);
    }

    public Optional<Integer> fetchFollowerCount(String channelId) {
        return fetchChannel(channelId)
            .map(ChzzkChannelResponse.Content::followerCount);
    }

    public Optional<ChzzkChannelResponse.Content> fetchChannel(String channelId) {
        rateLimiter.acquire();
        String uri = buildChannelApiUri(channelId);

        try {
            if (log.isDebugEnabled()) {
                log.debug("[Chzzk Channel] 채널 정보 요청 URL: {}", uri);
            }

            ChzzkChannelResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(ChzzkChannelResponse.class);

            if (response != null && response.content() != null) {
                return Optional.of(response.content());
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("[Chzzk Channel Error] 채널 정보 조회 실패. channelId: {}, 사유: {}", channelId, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildChannelApiUri(String channelId) {
        return UriComponentsBuilder.fromPath(channelFetchUrl)
            .buildAndExpand(channelId)
            .toUriString();
    }
}
