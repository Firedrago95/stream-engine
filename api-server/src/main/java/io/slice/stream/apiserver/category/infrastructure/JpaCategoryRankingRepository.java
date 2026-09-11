package io.slice.stream.apiserver.category.infrastructure;

import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCategoryRankingRepository extends JpaRepository<StreamSessionSegmentEntity, Long> {

    interface CategoryRankingProjection {
        String getCategoryName();
        Long getExactHours();
    }

    @Query(value = """
        SELECT 
            s.category_name AS categoryName,
            CAST(ROUND(SUM(v.viewer_count) / 240.0) AS BIGINT) AS exactHours
        FROM view_metric_timelines v
        JOIN stream_session_segments s 
            ON v.session_id = s.session_id
           AND v.timestamp >= s.started_at
           AND (s.ended_at IS NULL OR v.timestamp < s.ended_at)
        WHERE v.timestamp >= :since
          AND s.category_name IS NOT NULL
          AND s.category_name != ''
        GROUP BY s.category_name
        HAVING SUM(v.viewer_count) > 0
        ORDER BY exactHours DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<CategoryRankingProjection> findWeeklyCategoryRankings(
        @Param("since") Instant since,
        @Param("limit") int limit
    );
}
