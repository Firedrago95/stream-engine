package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnalysisRepositoryImpl implements AnalysisRepository {

    private final JpaAnalysisSignalRepository jpaRepository;

    @Override
    public void save(AnalysisSignal signal) {
        AnalysisSignalEntity entity = new AnalysisSignalEntity(
            signal.streamId(),
            signal.status(),
            signal.timestamp(),
            signal.firepower()
        );
        jpaRepository.save(entity);
    }

    @Override
    public List<AnalysisSignal> findRecentSignals(String streamId, int limit) {
        return jpaRepository.findByStreamIdOrderByTimestampDesc(streamId, PageRequest.of(0, limit))
            .stream()
            .map(e -> AnalysisSignal.of(
                e.getStreamId(),
                e.getStatus(),
                e.getTimestamp(),
                e.getFirepower()
            ))
            .toList();
    }

    @Override
    public Set<String> findChannelsWithRecentSignals(Collection<String> streamIds) {
        if (streamIds == null || streamIds.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findDistinctStreamIdByStreamIdIn(streamIds);
    }
}
