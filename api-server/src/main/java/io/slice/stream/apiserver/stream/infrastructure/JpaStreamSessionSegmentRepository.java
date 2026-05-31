package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStreamSessionSegmentRepository extends JpaRepository<StreamSessionSegmentEntity, Long> {

    @Query("""
        SELECT s FROM StreamSessionSegmentEntity s
        WHERE s.sessionId = :sessionId
          AND s.endedAt IS NULL
        """)
    Optional<StreamSessionSegmentEntity> findActiveSegment(@Param("sessionId") String sessionId);


    @Query("""
        SELECT s
        FROM StreamSessionSegmentEntity s 
        WHERE s.sessionId IN :sessionIds 
          AND s.endedAt IS NULL
        """)
    List<StreamSessionSegmentEntity> findAllActiveSegments(@Param("sessionIds") List<String> sessionIds);

    List<StreamSessionSegmentEntity> findBySessionIdOrderByStartedAtAsc(String sessionId);
}
