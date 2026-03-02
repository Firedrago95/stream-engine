package io.slice.stream.apiserver.analysis.infrastructure;

import io.slice.stream.apiserver.analysis.infrastructure.entity.AnalysisSignalEntity;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAnalysisSignalRepository extends JpaRepository<AnalysisSignalEntity, Long> {

    List<AnalysisSignalEntity> findByStreamIdOrderByTimestampDesc(String streamId, Pageable pageable);

    @Query("""
           SELECT DISTINCT a.streamId
           FROM AnalysisSignalEntity a
           WHERE a.streamId IN :streamIds
           """)
    Set<String> findDistinctStreamIdByStreamIdIn(@Param("streamIds") Collection<String> streamIds);
}
