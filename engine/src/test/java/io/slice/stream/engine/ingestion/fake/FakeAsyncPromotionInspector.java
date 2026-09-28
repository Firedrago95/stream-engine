package io.slice.stream.engine.ingestion.fake;

import io.slice.stream.engine.ingestion.application.AsyncPromotionInspector;
import io.slice.stream.engine.ingestion.domain.model.ChangedStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FakeAsyncPromotionInspector extends AsyncPromotionInspector {

    private final List<ChangedStream> inspectedStreams = new ArrayList<>();
    private int inspectCallCount = 0;

    public FakeAsyncPromotionInspector() {
        super(null, null, null, null);
    }

    @Override
    public void inspectChangedStreamsAsync(Collection<ChangedStream> changedStreams) {
        inspectCallCount++;
        if (changedStreams != null) {
            inspectedStreams.addAll(changedStreams);
        }
    }

    public List<ChangedStream> getInspectedStreams() {
        return new ArrayList<>(inspectedStreams);
    }

    public int getInspectCallCount() {
        return inspectCallCount;
    }
}
