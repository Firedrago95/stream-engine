package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;
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

    @Query("SELECT h FROM HighlightEventEntity h WHERE h.streamId = :streamId AND h.sessionId = :sessionId ORDER BY h.startTime ASC")
    List<HighlightEventEntity> findAllByStreamIdAndSessionId(@Param("streamId") String streamId, @Param("sessionId") String sessionId);

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
}
