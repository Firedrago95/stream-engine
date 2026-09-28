package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
            jpaRepository.save(toEntity(signal));
        } catch (DataAccessException e) {
            log.error("[DB Error] 분석 신호 저장 실패 - StreamId: {}, Error: {}", signal.streamId(), e.getMessage());
            throw e;
        }
    }

    @Override
    @Retryable(
        includes = DataAccessException.class,
        maxRetries = 2,
        delay = 1000
    )
    public void saveAll(List<AnalysisSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return;
        }

        try {
            List<AnalysisSignalEntity> entities = signals.stream()
                .map(this::toEntity)
                .toList();
            jpaRepository.saveAll(entities);
        } catch (DataAccessException e) {
            log.error("[DB Error] 분석 신호 목록 일괄 저장 실패 - 건수: {}, Error: {}", signals.size(), e.getMessage());
            throw e;
        }
    }

    private AnalysisSignalEntity toEntity(AnalysisSignal signal) {
        return new AnalysisSignalEntity(
            signal.streamId(),
            signal.sessionId(),
            signal.status(),
            signal.timestamp(),
            signal.firepower(),
            signal.offsetMs()
        );
    }

    @Override
    public List<AnalysisSignal> findRecentSignals(String streamId, int limit) {
        return jpaRepository.findByStreamIdOrderByTimestampDesc(streamId, PageRequest.of(0, limit))
            .stream()
            .map(e -> AnalysisSignal.of(
                e.getStreamId(),
                e.getSessionId(),
                e.getStatus(),
                e.getTimestamp(),
                e.getFirepower(),
                e.getOffsetMs()
            ))
            .toList();
    }

    @Override
    public Set<String> findChannelsWithRecentSignals(Collection<String> streamIds, Instant threshold) {
        if (streamIds == null || streamIds.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findDistinctStreamIdByStreamIdInAndTimestampAfter(streamIds, threshold);
    }

    @Override
    public List<AnalysisDataPoint> findRawHistory(String streamId, String sessionId) {
        return jpaRepository.findRawHistoryBySession(streamId, sessionId).stream()
            .map(e -> new AnalysisDataPoint(e.getTimestamp().toEpochMilli(), e.getFirepower(), e.getStatus(), e.getOffsetMs()))
            .toList();
    }

    @Override
    public List<AnalysisDataPoint> findSummaryHistory(String streamId, String sessionId) {
        return jpaRepository.findSummaryHistoryBySession(streamId, sessionId).stream()
            .map(p -> {
                if (p.getTimestampMinute() == null) return null;
                return new AnalysisDataPoint(
                    p.getTimestampMinute().toEpochMilli(),
                    p.getFirepowerMax(),
                    p.getStatus(),
                    p.getOffsetMs()
                );
            })
            .filter(Objects::nonNull)
            .toList();
    }
}
