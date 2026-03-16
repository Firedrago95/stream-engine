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
        INSERT INTO analysis_signals_summary (stream_id, session_id, status, firepower_avg, firepower_max, timestamp_minute, offset_ms)
        SELECT
             MAX(stream_id),
             session_id,
             status,
             CAST(AVG(firepower) AS BIGINT),
             MAX(firepower),
             MIN(timestamp) AS timestamp_minute,
             (CAST(FLOOR(offset_ms / 60000.0) AS BIGINT) * 60000) AS offset_ms
        FROM analysis_signals
        WHERE timestamp < :cutoffTime
        GROUP BY session_id, status, FLOOR(offset_ms / 60000.0)
        ON CONFLICT (session_id, status, offset_ms)
        DO UPDATE SET
           firepower_avg = EXCLUDED.firepower_avg,
           firepower_max = EXCLUDED.firepower_max,
           timestamp_minute = LEAST(analysis_signals_summary.timestamp_minute, EXCLUDED.timestamp_minute)
        """, nativeQuery = true)
    int rollupOldSignals(@Param("cutoffTime") Instant cutoffTime);

    @Modifying
    @Query("DELETE FROM AnalysisSignalEntity a WHERE a.timestamp < :cutoffTime")
    int deleteOlderThan(@Param("cutoffTime") Instant cutoffTime);

    @Query(value = """
        SELECT combined.day
        FROM (
            SELECT DATE((timestamp - INTERVAL '6 hours') AT TIME ZONE 'Asia/Seoul') AS day
            FROM analysis_signals
            WHERE stream_id = :streamId
              AND timestamp >= CURRENT_TIMESTAMP - INTERVAL '3 days'
              AND DATE((timestamp - INTERVAL '6 hours') AT TIME ZONE 'Asia/Seoul') < :beforeDate
            UNION
            SELECT DATE((timestamp_minute - INTERVAL '6 hours') AT TIME ZONE 'Asia/Seoul') AS day
            FROM analysis_signals_summary
            WHERE stream_id = :streamId
              AND DATE((timestamp_minute - INTERVAL '6 hours') AT TIME ZONE 'Asia/Seoul') < :beforeDate
        ) AS combined
        GROUP BY combined.day
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
    List<AnalysisSignalEntity> findRawHistoryByDate(
        @Param("streamId") String streamId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    interface SummaryDataProjection {

        Timestamp getTimestampMinute();

        Long getFirepowerMax();

        String getStatus();

        Long getOffsetMs();
    }

    @Query(value = """
        SELECT 
            timestamp_minute AS timestampMinute, 
            firepower_max AS firepowerMax, 
            status AS status,
            offset_ms AS offsetMs -- [추가]
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
