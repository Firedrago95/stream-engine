package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.core.event.StreamChangedEvent;
import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
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
        List<StreamTarget> streamTargets = streamDiscoveryClient.fetchTopLiveStreams(discoveryLimit);
        if (streamTargets.isEmpty()) return;

        // 2. 외부 동기화 (API 서버)
        syncToApiServer(streamTargets);

        // 3. 내부 상태 업데이트 및 이벤트 처리
        StreamUpdateResults updateResults = streamRepository.update(streamTargets);
        publishEventIfChanged(updateResults);
    }

    private void syncToApiServer(List<StreamTarget> streamTargets) {
        List<StreamSyncRequest> syncRequests = streamTargets.stream()
            .map(StreamSyncRequest::from) // DTO 내부에 static factory 메서드 사용 권장
            .toList();

        apiServerClient.syncStreams(syncRequests);
    }

    private void publishEventIfChanged(StreamUpdateResults results) {
        if (!results.newStreamIds().isEmpty() || !results.closedStreamIds().isEmpty()) {
            log.info("[Event] 방송 상태 변경 감지 - 신규: {}, 종료: {}",
                results.newStreamIds().size(), results.closedStreamIds().size());

            eventPublisher.publishEvent(new StreamChangedEvent(
                results.newStreamIds(),
                results.closedStreamIds()
            ));
        }
    }
}
