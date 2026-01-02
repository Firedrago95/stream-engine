package io.slice.stream.engine.ingestion.application;

import io.slice.stream.engine.core.model.StreamTarget;
import io.slice.stream.engine.ingestion.domain.client.StreamDiscoveryClient;
import io.slice.stream.engine.ingestion.domain.repository.StreamRepository;
import io.slice.stream.engine.ingestion.domain.event.StreamStartedEvent;
import io.slice.stream.engine.ingestion.domain.event.StreamStoppedEvent;
import io.slice.stream.engine.ingestion.domain.model.StreamUpdateResults;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final int DISCOVERY_LIMIT = 20;

    private final StreamDiscoveryClient streamDiscoveryClient;
    private final StreamRepository streamRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 30000)
    public void ingest() {
        List<StreamTarget> streamTargets = streamDiscoveryClient.fetchTopLiveStreams(DISCOVERY_LIMIT);
        StreamUpdateResults updateResults = streamRepository.update(streamTargets);

        updateResults.newStreamIds().forEach(id ->
            eventPublisher.publishEvent(new StreamStartedEvent(id))
        );

        updateResults.closedStreamIds().forEach(id ->
            eventPublisher.publishEvent(new StreamStoppedEvent(id))
        );
    }
}
