package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
        SELECT ss FROM StreamSessionEntity ss 
        JOIN StreamEntity s ON ss.streamId = s.streamId 
        WHERE ss.endedAt IS NULL 
          AND s.lastUpdateAt < :threshold
        """)
    List<StreamSessionEntity> findSessionsToClose(@Param("threshold") Instant threshold);

    @Query("SELECT ss FROM StreamSessionEntity ss WHERE ss.streamId = :streamId ORDER BY ss.startedAt DESC")
    List<StreamSessionEntity> findRecentSessionsByStreamId(@Param("streamId") String streamId, org.springframework.data.domain.Pageable pageable);

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
}
