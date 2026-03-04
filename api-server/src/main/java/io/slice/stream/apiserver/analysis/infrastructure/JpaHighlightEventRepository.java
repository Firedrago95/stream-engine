package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaHighlightEventRepository extends JpaRepository<HighlightEventEntity, Long> {

    @Query("""
          SELECT h FROM HighlightEventEntity h
          WHERE h.streamId = :streamId AND h.status = :status
          ORDER BY h.startTime DESC LIMIT 1
          """)
    Optional<HighlightEventEntity> findOngoingSession(
        @Param("streamId") String streamId,
        @Param("status") String status
    );
}
