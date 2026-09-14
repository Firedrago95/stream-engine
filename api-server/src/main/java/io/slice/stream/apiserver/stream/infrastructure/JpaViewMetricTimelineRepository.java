package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaViewMetricTimelineRepository extends JpaRepository<ViewMetricTimelineEntity, Long> {

    List<ViewMetricTimelineEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    @Query("SELECT AVG(v.viewerCount) FROM ViewMetricTimelineEntity v WHERE v.sessionId = :sessionId")
    Double findAverageViewerCountBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT MAX(v.viewerCount) FROM ViewMetricTimelineEntity v WHERE v.sessionId = :sessionId")
    Integer findPeakViewerCountBySessionId(@Param("sessionId") String sessionId);

    @Query("""
           SELECT v.streamId 
           FROM ViewMetricTimelineEntity v 
           WHERE v.timestamp >= :since 
           GROUP BY v.streamId 
           ORDER BY AVG(v.viewerCount) DESC
           """)
    List<String> findTopStreamIdsByAverageViewerCountSince(@Param("since") Instant since, Pageable pageable);
}
