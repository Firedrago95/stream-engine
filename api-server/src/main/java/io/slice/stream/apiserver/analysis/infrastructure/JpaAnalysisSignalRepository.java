package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

    @Query(value = """
        SELECT combined.day
        FROM (
            SELECT DATE(timestamp AT TIME ZONE 'Asia/Seoul') AS day
            FROM analysis_signals
            WHERE stream_id = :streamId
              AND timestamp >= CURRENT_TIMESTAMP - INTERVAL '3 days'
            UNION
            SELECT DATE(timestamp AT TIME ZONE 'Asia/Seoul') AS day
            FROM analysis_signals_summary
            WHERE stream_id = :streamId
        ) combined.day < :beforeDate
        ORDER BY combined.day DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<LocalDate> findAvailableDatesWithCursor(
        @Param("streamId") String streamId,
        @Param("beforeDate") LocalDate beforeDate,
        @Param("limit") int limit
    );

    @Query("""
           SELECT a FROM AnalysisSignalEntity  a
           WHERE a.streamId = :streamId
           AND a.timestamp >= :start
           AND a.timestamp < :end
           ORDER BY a.timestamp ASC
           """)
    List<AnalysisSignalEntity> findRawHistoryByData(
        @Param("streamId") String streamId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    interface SummaryDataProjection {
        Timestamp getTimestampMinute();
        Long getFirepowerMax();
        String getStatus();
    }

    @Query(value = """
           SELECT timestamp_minute AS timestampMinute, firepower_max AS firepowerMax, status AS status
           FROM analysis_signals_summary
           WHERE stream_id = :streamId
           AND timestamp_minute >= :start AND timestamp_minute < :end
           ORDER BY timestamp_minute ASC
           """, nativeQuery = true)
    List<SummaryDataProjection> findSummaryHistoryByDate(
        @Param("streamId") String streamId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
}
