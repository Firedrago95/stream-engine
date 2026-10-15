package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.stream.presentation.dto.StreamSyncRequest;
import java.time.Instant;
import java.util.ArrayList;
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

    private final StreamSyncDirtyChecker dirtyChecker;
    private final StreamSyncPersistenceService persistenceService;

    public void syncAll(List<StreamSyncRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Map<String, StreamSyncRequest> uniqueRequests = deduplicateRequests(requests);
        List<StreamSyncRequest> requestList = new ArrayList<>(uniqueRequests.values());

        List<StreamSyncRequest> dirtyRequests = dirtyChecker.filterDirtyRequests(requestList);

        Instant currentTime = Instant.now();
        persistenceService.persistSync(dirtyRequests, uniqueRequests, currentTime);

        dirtyChecker.updateSnapshots(dirtyRequests);
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
