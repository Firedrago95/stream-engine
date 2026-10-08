package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaHighlightEventRepository extends JpaRepository<HighlightEventEntity, Long> {

    Optional<HighlightEventEntity> findFirstByStreamIdAndStatusOrderByStartTimeDesc(
        @Param("streamId") String streamId,
        @Param("status") String status
    );

    long countByStartTimeAfter(Instant threshold);

    @Query("SELECT h FROM HighlightEventEntity h WHERE h.streamId = :streamId AND h.sessionId = :sessionId")
    List<HighlightEventEntity> findAllByStreamIdAndSessionId(@Param("streamId") String streamId, @Param("sessionId") String sessionId);

    @Query("SELECT h FROM HighlightEventEntity h WHERE h.streamId = :streamId AND h.sessionId IN :sessionIds")
    List<HighlightEventEntity> findAllByStreamIdAndSessionIdIn(@Param("streamId") String streamId, @Param("sessionIds") Collection<String> sessionIds);

    @Query("""
           SELECT h FROM HighlightEventEntity h
           WHERE h.status = 'ONGOING'
             AND h.lastPeakTime < :threshold
           """)
    List<HighlightEventEntity> findZombieSessions(@Param("threshold") Instant threshold);

    @Modifying
    @Query(value = """
    DELETE FROM highlight_events 
    WHERE session_id = :sessionId 
      AND id NOT IN (
          SELECT id FROM highlight_events 
          WHERE session_id = :sessionId 
          ORDER BY peak_firepower DESC 
          LIMIT :retentionLimit
      )
    """, nativeQuery = true)
    int deleteExceptTop(@Param("sessionId") String sessionId, @Param("retentionLimit") int retentionLimit);

    @Modifying
    @Query(value = """
        DELETE FROM highlight_events 
        WHERE id IN (
            SELECT id FROM (
                SELECT id, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY peak_firepower DESC) as rn
                FROM highlight_events 
                WHERE start_time < :threshold
            ) sub
            WHERE sub.rn > :retentionLimit
        )
        """, nativeQuery = true)
    int compressOldHighlightsExceptTop(
        @Param("threshold") Instant threshold,
        @Param("retentionLimit") int retentionLimit
    );

    @Modifying
    @Query("DELETE FROM HighlightEventEntity h WHERE h.startTime < :expiredThreshold")
    int deleteExpiredHighlights(@Param("expiredThreshold") Instant expiredThreshold);

    @Modifying
    @Query("DELETE FROM HighlightEventEntity h WHERE h.sessionId IN :sessionIds")
    int deleteAllBySessionIds(@Param("sessionIds") List<String> sessionIds);
}
