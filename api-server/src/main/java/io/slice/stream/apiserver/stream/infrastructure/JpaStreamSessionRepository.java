package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStreamSessionRepository extends JpaRepository<StreamSessionEntity, Long> {

    @Query("""
        SELECT ss
        FROM StreamSessionEntity ss
        WHERE ss.streamId = :streamId
          AND ss.endedAt IS NULL
        ORDER BY ss.startedAt DESC
        LIMIT 1
        """)
    Optional<StreamSessionEntity> findActiveSession(@Param("streamId") String streamId);

    Optional<StreamSessionEntity> findBySessionId(String sessionId);

    List<StreamSessionEntity> findAllBySessionIdIn(Collection<String> sessionIds);

    @Query("""
        SELECT ss FROM StreamSessionEntity ss 
        JOIN StreamEntity s ON ss.streamId = s.streamId 
        WHERE ss.endedAt IS NULL 
        AND s.lastUpdateAt < :threshold
        """)
    List<StreamSessionEntity> findSessionsToClose(@Param("threshold") Instant threshold);

    Page<StreamSessionEntity> findByStreamIdOrderByStartedAtDesc(String streamId, Pageable pageable);

    Page<StreamSessionEntity> findByStreamIdAndPaidPromotionTrueOrderByStartedAtDesc(String streamId, Pageable pageable);

    @Query("SELECT ss FROM StreamSessionEntity ss WHERE ss.streamId = :streamId AND ss.startedAt >= :since")
    List<StreamSessionEntity> findSessionsSince(@Param("streamId") String streamId, @Param("since") Instant since);

    @Query("SELECT ss FROM StreamSessionEntity ss WHERE ss.streamId = :streamId ORDER BY ss.startedAt DESC")
    List<StreamSessionEntity> findRecentSessionsByStreamId(@Param("streamId") String streamId, Pageable pageable);

    @Query("""
        SELECT ss
        FROM StreamSessionEntity ss
        WHERE ss.endedAt IS NOT NULL
          AND ss.endedAt < :threshold
        """)
    List<StreamSessionEntity> findFinishedSessionsOlderThan(@Param("threshold") Instant threshold);

    @Query("""
       SELECT ss
       FROM StreamSessionEntity ss
       WHERE ss.streamId IN :streamIds
         AND ss.endedAt IS NULL
           """)
    List<StreamSessionEntity> findAllActiveSessions(@Param("streamIds") List<String> streamIds);

    @Query("""
           SELECT ss
           FROM StreamSessionEntity ss
           WHERE ss.streamId = :streamId
             AND ss.sessionId = :sessionId
             AND ss.endedAt IS NULL
           """)
    Optional<StreamSessionEntity> findActiveSession(
        @Param("streamId") String streamId,
        @Param("sessionId") String sessionId
    );

    @Modifying
    @Query("DELETE FROM StreamSessionEntity ss WHERE ss.endedAt IS NOT NULL AND ss.endedAt < :threshold")
    int deleteExpiredSessions(@Param("threshold") Instant threshold);

    @Query("""
        SELECT ss FROM StreamSessionEntity ss
        WHERE ss.streamId = :streamId
          AND ss.startedAt < :rangeEnd
          AND (ss.endedAt IS NULL OR ss.endedAt > :rangeStart)
        ORDER BY ss.startedAt ASC
        """)
    List<StreamSessionEntity> findSessionsOverlapping(
        @Param("streamId") String streamId,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    @Query("""
        SELECT ss FROM StreamSessionEntity ss
        WHERE ss.endedAt IS NULL
          AND ss.startedAt < :threshold
        ORDER BY ss.startedAt ASC
        """)
    List<StreamSessionEntity> findActiveSessionsStartedBefore(@Param("threshold") Instant threshold);

    @Query("SELECT DISTINCT ss.streamId FROM StreamSessionEntity ss WHERE ss.startedAt >= :since")
    List<String> findDistinctStreamIdsByStartedAtAfter(@Param("since") Instant since);
}

