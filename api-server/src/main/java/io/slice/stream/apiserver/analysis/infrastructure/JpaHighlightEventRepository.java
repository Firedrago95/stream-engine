package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaHighlightEventRepository extends JpaRepository<HighlightEventEntity, Long> {

    Optional<HighlightEventEntity> findFirstByStreamIdAndStatusOrderByStartTimeDesc(
        @Param("streamId") String streamId,
        @Param("status") String status
    );

    @Query("""
           SELECT h FROM HighlightEventEntity h
           WHERE h.streamId = :streamId
           AND h.startTime >= :start
           AND h.startTime < :end
           ORDER BY h.startTime ASC
           """)
    List<HighlightEventEntity> findAllByStreamIdAndDateRange(
        @Param("streamId") String streamId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
}
