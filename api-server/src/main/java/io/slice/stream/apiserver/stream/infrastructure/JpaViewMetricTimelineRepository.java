package io.slice.stream.apiserver.stream.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaViewMetricTimelineRepository extends JpaRepository<ViewMetricTimelineEntity, Long> {

    List<ViewMetricTimelineEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<ViewMetricTimelineEntity> findBySessionIdInOrderByTimestampAsc(Collection<String> sessionIds);

    @Query("SELECT AVG(v.viewerCount) FROM ViewMetricTimelineEntity v WHERE v.sessionId = :sessionId")
    Double findAverageViewerCountBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT MAX(v.viewerCount) FROM ViewMetricTimelineEntity v WHERE v.sessionId = :sessionId")
    Integer findPeakViewerCountBySessionId(@Param("sessionId") String sessionId);
}

