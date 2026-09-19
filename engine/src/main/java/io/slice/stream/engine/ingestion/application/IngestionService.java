package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.domain.service.StreamUpdateAnalyzer;
import io.slice.stream.engine.ingestion.domain.targeting.TargetStreamPool;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final StreamDiscoveryClient streamDiscoveryClient;
    private final StreamRepository streamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ApiServerClient apiServerClient;
    private final StreamUpdateAnalyzer streamUpdateAnalyzer;
    private final TargetStreamPool targetStreamPool;

    @Value("${chzzk.discovery.limit}")
    private int discoveryLimit;

    @Scheduled(fixedRate = 30000)
    public void ingest() {
        try {
            List<StreamTarget> topLiveStreams = streamDiscoveryClient.fetchTopLiveStreams(discoveryLimit);
            if (topLiveStreams.isEmpty()) {
                log.warn("[Ingestion] 치지직 상위 방송 API가 빈 목록을 반환했습니다. 이번 수집 사이클을 건너뜁니다.");
                return;
            }
            Instant now = Instant.now();
            Set<String> activeChannelIds = streamRepository.getActiveChannelIds();

            List<StreamTarget> activeStreamTargets = streamRepository.getStreamTargets(
                new ArrayList<>(activeChannelIds)
            );

            Set<String> activeTargetChannels = targetStreamPool.getAllActiveTargetChannels();
            List<StreamTarget> targetLiveStreams = topLiveStreams.stream()
                .filter(stream -> activeTargetChannels.contains(stream.channelId()))
                .toList();

            List<StreamTarget> currentTargetStreams = resolveCurrentTargetStreams(targetLiveStreams, activeStreamTargets);

            StreamUpdateResults updateResults = streamUpdateAnalyzer.analyze(
                currentTargetStreams,
                activeChannelIds,
                activeStreamTargets,
                now
            );

            streamRepository.sync(updateResults.closedStreamIds(), currentTargetStreams);

            handleExternalSync(topLiveStreams, currentTargetStreams, updateResults);
            handleEvents(updateResults);
        } catch (Exception e) {
            log.error("[Ingestion] 수집 주기 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    private List<StreamTarget> resolveCurrentTargetStreams(
        List<StreamTarget> targetLiveStreams,
        List<StreamTarget> activeStreamTargets
    ) {
        Map<String, StreamTarget> activeTargetMap = activeStreamTargets.stream()
            .collect(Collectors.toMap(StreamTarget::channelId, target -> target, (existing, replacing) -> existing));

        Set<String> newChannelIds = new HashSet<>();
        List<StreamTarget> resolvedTargets = new ArrayList<>();

        for (StreamTarget liveTarget : targetLiveStreams) {
            StreamTarget oldTarget = activeTargetMap.get(liveTarget.channelId());
            if (isSameLiveSession(oldTarget, liveTarget)) {
                resolvedTargets.add(liveTarget.withChatChannelId(oldTarget.chatChannelId()));
            } else {
                newChannelIds.add(liveTarget.channelId());
            }
        }

        if (!newChannelIds.isEmpty()) {
            List<StreamTarget> newDetailedStreams = streamDiscoveryClient.fetchLiveStreams(newChannelIds);
            resolvedTargets.addAll(newDetailedStreams);
        }

        return resolvedTargets;
    }

    private boolean isSameLiveSession(StreamTarget oldTarget, StreamTarget newTarget) {
        return oldTarget != null
            && oldTarget.liveId() == newTarget.liveId()
            && oldTarget.chatChannelId() != null
            && !oldTarget.chatChannelId().isBlank();
    }

    private void handleExternalSync(List<StreamTarget> allLiveTargets, List<StreamTarget> detailedTargets, StreamUpdateResults results) {
        Map<String, StreamTarget> detailedMap = detailedTargets.stream()
            .collect(Collectors.toMap(StreamTarget::channelId, t -> t, (a, b) -> a));

        List<StreamTarget> mergedTargets = allLiveTargets.stream()
            .map(live -> detailedMap.getOrDefault(live.channelId(), live))
            .toList();

        apiServerClient.syncStreams(mergedTargets.stream().map(StreamSyncRequest::from).toList());

        if (!results.changedStreams().isEmpty()) {
            apiServerClient.recordNewSegments(new ArrayList<>(results.changedStreams()));
        }
    }

    private void handleEvents(StreamUpdateResults results) {
        if (!results.newStreams().isEmpty() || !results.closedStreamIds().isEmpty()) {
            log.info("[Event] 방송 상태 변경 - 신규: {}, 종료: {}",
                results.newStreams().size(), results.closedStreamIds().size());

            eventPublisher.publishEvent(new StreamChangedEvent(
                results.newStreams(),
                results.closedStreamIds(),
                results.changedAt()
            ));
        }
    }
}
