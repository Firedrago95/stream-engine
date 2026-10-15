package io.slice.stream.collector.infrastructure.chzzk;

import io.slice.stream.collector.domain.LiveStatusClient;
import io.slice.stream.collector.domain.LiveStatusResult;
import io.slice.stream.collector.infrastructure.chzzk.dto.response.ChzzkLiveStatusResponse;
import io.slice.stream.core.model.StreamTarget;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    public LiveStatusResult fetchLiveStatuses(Set<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return LiveStatusResult.empty();
        }

        List<CompletableFuture<SingleStatusResult>> futures = channelIds.stream()
            .map(channelId -> CompletableFuture.supplyAsync(() -> fetchSingleStatus(channelId), executor))
            .toList();

        List<SingleStatusResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        return aggregateResults(results);
    }

    private LiveStatusResult aggregateResults(List<SingleStatusResult> results) {
        List<StreamTarget> openStreams = new ArrayList<>();
        Set<String> closedChannelIds = new HashSet<>();
        Set<String> failedChannelIds = new HashSet<>();

        for (SingleStatusResult result : results) {
            switch (result) {
                case SingleStatusResult.Open open -> openStreams.add(open.target());
                case SingleStatusResult.Closed closed -> closedChannelIds.add(closed.channelId());
                case SingleStatusResult.Failed failed -> failedChannelIds.add(failed.channelId());
            }
        }

        return new LiveStatusResult(openStreams, closedChannelIds, failedChannelIds);
    }

    private SingleStatusResult fetchSingleStatus(String channelId) {
        try {
            ChzzkLiveStatusResponse response = restClient.get()
                .uri(liveStatusPollingUri, channelId)
                .retrieve()
                .body(ChzzkLiveStatusResponse.class);

            if (response == null || response.content() == null) {
                return new SingleStatusResult.Closed(channelId);
            }

            ChzzkLiveStatusResponse.Content content = response.content();
            if (!"OPEN".equals(content.status())) {
                return new SingleStatusResult.Closed(channelId);
            }

            String chatChannelId = content.chatChannelId();
            if (chatChannelId == null || chatChannelId.isBlank()) {
                return new SingleStatusResult.Closed(channelId);
            }

            Instant startedAt = content.openDate() != null
                ? content.openDate().toInstant(ZoneOffset.of("+09:00"))
                : Instant.now();

            long liveId = content.liveId() != null ? content.liveId() : 0L;
            boolean adult = Boolean.TRUE.equals(content.adult());

            StreamTarget target = new StreamTarget(
                channelId,
                channelId,
                chatChannelId,
                liveId,
                content.liveTitle(),
                content.concurrentUserCount(),
                null,
                content.liveCategoryValue(),
                startedAt,
                adult
            );

            return new SingleStatusResult.Open(target);
        } catch (Exception e) {
            log.warn("[상태 조회 실패] 채널 ID: {}, 사유: {}", channelId, e.getMessage());
            return new SingleStatusResult.Failed(channelId, e.getMessage());
        }
    }

    private sealed interface SingleStatusResult {
        record Open(StreamTarget target) implements SingleStatusResult {}
        record Closed(String channelId) implements SingleStatusResult {}
        record Failed(String channelId, String reason) implements SingleStatusResult {}
    }
}
