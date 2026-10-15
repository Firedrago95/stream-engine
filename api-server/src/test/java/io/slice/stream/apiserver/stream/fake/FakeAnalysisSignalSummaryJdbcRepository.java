package io.slice.stream.apiserver.stream.fake;

import io.slice.stream.apiserver.analysis.infrastructure.AnalysisSignalSummaryJdbcRepository;
import io.slice.stream.apiserver.analysis.infrastructure.dto.AnalysisSignalSummaryDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeAnalysisSignalSummaryJdbcRepository extends AnalysisSignalSummaryJdbcRepository {

    private final List<AnalysisSignalSummaryDto> storage = Collections.synchronizedList(new ArrayList<>());

    public FakeAnalysisSignalSummaryJdbcRepository() {
        super(null);
    }

    @Override
    public void batchUpsertSummaries(List<AnalysisSignalSummaryDto> summaries) {
        if (summaries != null) {
            storage.addAll(summaries);
        }
    }

    public List<AnalysisSignalSummaryDto> getStorage() {
        return new ArrayList<>(storage);
    }

    public void clear() {
        storage.clear();
    }
}
