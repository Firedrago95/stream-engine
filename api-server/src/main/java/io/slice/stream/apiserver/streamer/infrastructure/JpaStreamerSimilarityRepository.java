package io.slice.stream.apiserver.streamer.infrastructure;

import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerSimilarityEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStreamerSimilarityRepository extends JpaRepository<StreamerSimilarityEntity, Long> {

    List<StreamerSimilarityEntity> findByStreamIdAndCalculatedDateOrderByRankOrderAsc(
        String streamId,
        LocalDate calculatedDate
    );

    @Query("""
        SELECT s FROM StreamerSimilarityEntity s
        WHERE s.streamId = :streamId
          AND s.calculatedDate = (
              SELECT MAX(sub.calculatedDate) FROM StreamerSimilarityEntity sub WHERE sub.streamId = :streamId
          )
        ORDER BY s.rankOrder ASC
    """)
    List<StreamerSimilarityEntity> findLatestSimilaritiesByStreamId(@Param("streamId") String streamId);

    @Modifying
    @Query("DELETE FROM StreamerSimilarityEntity s WHERE s.calculatedDate = :calculatedDate")
    void deleteByCalculatedDate(@Param("calculatedDate") LocalDate calculatedDate);
}
