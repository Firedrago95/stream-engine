package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final StreamSyncPersistenceService persistenceService;

    public void syncAll(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Map<String, StreamSyncRequest> uniqueRequests = deduplicateRequests(requests);
        Instant currentTime = Instant.now();
        persistenceService.persistSync(uniqueRequests, currentTime);
    }

    private Map<String, StreamSyncRequest> deduplicateRequests(List<StreamSyncRequest> requests) {
        return requests.stream()
            .collect(Collectors.toMap(
                StreamSyncRequest::streamId,
                req -> req,
                (existing, replacement) -> replacement
            ));
    }
}
