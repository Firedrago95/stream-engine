package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.domain.service.StreamUpdateAnalyzer;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

            Set<String> topLiveStreamIds = topLiveStreams.stream().map(StreamTarget::channelId).collect(Collectors.toSet());
            Set<String> dropoutIds = new HashSet<>(activeChannelIds);
            dropoutIds.removeAll(topLiveStreamIds);

            List<StreamTarget> rankoutStreams = streamDiscoveryClient.fetchLiveStreams(dropoutIds);

            List<StreamTarget> currentTargets = new ArrayList<>(topLiveStreams);
            currentTargets.addAll(rankoutStreams);

            List<StreamTarget> activeStreamTargets = streamRepository.getStreamTargets(
                new ArrayList<>(activeChannelIds)
            );

            StreamUpdateResults updateResults = streamUpdateAnalyzer.analyze(
                currentTargets,
                activeChannelIds,
                activeStreamTargets,
                now
            );

            streamRepository.sync(updateResults.closedStreamIds(), currentTargets);

            handleExternalSync(currentTargets, updateResults);
            handleEvents(updateResults);
        } catch (Exception e) {
            log.error("[Ingestion] 수집 주기 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    private void handleExternalSync(List<StreamTarget> targets, StreamUpdateResults results) {
        apiServerClient.syncStreams(targets.stream().map(StreamSyncRequest::from).toList());

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
