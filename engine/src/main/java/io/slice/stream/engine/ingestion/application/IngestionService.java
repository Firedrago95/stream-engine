package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.util.ArrayList;
import java.util.List;
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

    @Value("${chzzk.discovery.limit}")
    private int discoveryLimit;

    @Scheduled(fixedRate = 30000)
    public void ingest() {
        // 1. 데이터 수집
        List<StreamTarget> currentTargets = streamDiscoveryClient.fetchTopLiveStreams(discoveryLimit);
        if (currentTargets.isEmpty()) return;

        // 2. 원자적 상태 업데이트
        StreamUpdateResults results = streamRepository.update(currentTargets);

        // 3. 사이드 이펙트 처리 (Orchestration)
        handleExternalSync(currentTargets, results);
        handleEvents(results);
    }

    private void handleExternalSync(List<StreamTarget> targets, StreamUpdateResults results) {
        apiServerClient.syncStreams(targets.stream().map(StreamSyncRequest::from).toList());

        if (!results.changedStreams().isEmpty()) {
            apiServerClient.recordNewSegments(new ArrayList<>(results.changedStreams()));
        }
    }

    private void handleEvents(StreamUpdateResults results) {
        if (!results.newStreamIds().isEmpty() || !results.closedStreamIds().isEmpty()) {
            log.info("[Event] 방송 상태 변경 - 신규: {}, 종료: {}",
                results.newStreamIds().size(), results.closedStreamIds().size());

            eventPublisher.publishEvent(new StreamChangedEvent(
                results.newStreamIds(),
                results.closedStreamIds()
            ));
        }
    }
}
