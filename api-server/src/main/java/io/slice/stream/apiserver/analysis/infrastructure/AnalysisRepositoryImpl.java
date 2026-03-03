package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisRepositoryImpl implements AnalysisRepository {

    private final JpaAnalysisSignalRepository jpaRepository;

    @Override
    @Retryable(
        includes = DataAccessException.class,
        maxRetries = 2,
        delay = 1000
    )
    public void save(AnalysisSignal signal) {
        try {
            AnalysisSignalEntity entity = new AnalysisSignalEntity(
                signal.streamId(),
                signal.status(),
                signal.timestamp(),
                signal.firepower()
            );
            jpaRepository.save(entity);
        } catch (DataAccessException e) {
            log.error("[DB Error] 분석 신호 저장 실패 - StreamId: {}, Error: {}", signal.streamId(), e.getMessage());
            throw e;
        }
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
