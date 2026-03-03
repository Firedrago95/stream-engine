package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAnalysisSignalRepository extends JpaRepository<AnalysisSignalEntity, Long> {

    List<AnalysisSignalEntity> findByStreamIdOrderByTimestampDesc(String streamId, Pageable pageable);

    @Query("""
           SELECT DISTINCT a.streamId
           FROM AnalysisSignalEntity a
           WHERE a.streamId IN :streamIds
           """)
    Set<String> findDistinctStreamIdByStreamIdIn(@Param("streamIds") Collection<String> streamIds);

    @Modifying
    @Query(value = """
                   INSERT INTO analysis_signals_summary (stream_id, status, firepower_avg, firepower_max, timestamp_minute)
                   SELECT
                        stream_id,
                        status,
                        CAST(AVG(firepower) AS BIGINT),
                        MAX(firepower),
                        date_trunc('minute', timestamp)
                   FROM analysis_signals
                   WHERE timestamp < :cutoffTime
                   GROUP BY stream_id, status, date_trunc('minute', timestamp)
                   ON CONFLICT (stream_id, status, timestamp_minute)
                   DO UPDATE SET
                      firepower_avg = EXCLUDED.firepower_avg,
                      firepower_max = EXCLUDED.firepower_max
                   """, nativeQuery = true)
    int rollupOldSignals(@Param("cutoffTime") Instant cutoffTime);

    @Modifying
    @Query("DELETE FROM AnalysisSignalEntity a WHERE a.timestamp < :cutoffTime")
    int deleteOlderThan(@Param("cutoffTime") Instant cutoffTime);
}
