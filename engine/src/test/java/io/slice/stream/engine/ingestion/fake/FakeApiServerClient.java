package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.ApiServerClient;
import io.slice.stream.engine.ingestion.infrastructure.apiServer.dto.StreamSyncRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeApiServerClient extends ApiServerClient {

    private final List<StreamSyncRequest> lastSyncedRequests = new ArrayList<>();
    private final List<ChangedStream> lastRecordedSegments = new ArrayList<>();
    private int syncStreamsCallCount = 0;
    private int recordSegmentsCallCount = 0;

    public FakeApiServerClient() {
        super(null, "", "", "", "");
    }

    @Override
    public void syncStreams(List<StreamSyncRequest> requests) {
        syncStreamsCallCount++;
        lastSyncedRequests.clear();
        if (requests != null) {
            lastSyncedRequests.addAll(requests);
        }
    }

    @Override
    public void recordNewSegments(List<ChangedStream> changedStreams) {
        recordSegmentsCallCount++;
        lastRecordedSegments.clear();
        if (changedStreams != null) {
            lastRecordedSegments.addAll(changedStreams);
        }
    }

    public List<StreamSyncRequest> getLastSyncedRequests() {
        return Collections.unmodifiableList(lastSyncedRequests);
    }

    public List<ChangedStream> getLastRecordedSegments() {
        return Collections.unmodifiableList(lastRecordedSegments);
    }

    public int getSyncStreamsCallCount() {
        return syncStreamsCallCount;
    }

    public int getRecordSegmentsCallCount() {
        return recordSegmentsCallCount;
    }
}
