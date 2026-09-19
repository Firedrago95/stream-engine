package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.domain.LiveStatusClient;
import io.slice.stream.collector.infrastructure.chzzk.dto.response.ChzzkLiveStatusResponse;
import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChzzkLiveStatusClient implements LiveStatusClient {

    private final RestClient restClient;
    private final Executor executor;

    @Value("${chzzk.api.live-status-polling:/polling/v2/channels/{channelId}/live-status}")
    private String liveStatusPollingUri = "/polling/v2/channels/{channelId}/live-status";

    @Override
    public List<StreamTarget> fetchOpenStreams(Set<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<StreamTarget>> futures = channelIds.stream()
            .map(channelId -> CompletableFuture.supplyAsync(() -> fetchSingleStatus(channelId), executor))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    private StreamTarget fetchSingleStatus(String channelId) {
        try {
            ChzzkLiveStatusResponse response = restClient.get()
                .uri(liveStatusPollingUri, channelId)
                .retrieve()
                .body(ChzzkLiveStatusResponse.class);

            if (response == null || response.content() == null) {
                return null;
            }

            ChzzkLiveStatusResponse.Content content = response.content();
            if (!"OPEN".equals(content.status())) {
                return null;
            }

            String chatChannelId = content.chatChannelId();
            if (chatChannelId == null || chatChannelId.isBlank()) {
                return null;
            }

            Instant startedAt = content.openDate() != null
                ? content.openDate().toInstant(ZoneOffset.of("+09:00"))
                : Instant.now();

            long liveId = content.liveId() != null ? content.liveId() : 0L;

            return new StreamTarget(
                channelId,
                channelId,
                chatChannelId,
                liveId,
                content.liveTitle(),
                content.concurrentUserCount(),
                null,
                content.liveCategoryValue(),
                startedAt
            );
        } catch (Exception e) {
            log.warn("[상태 조회 실패] 채널 ID: {}, 사유: {}", channelId, e.getMessage());
            return null;
        }
    }
}
