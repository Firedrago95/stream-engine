package io.slice.stream.apiserver.streamer.domain.repository;

import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreamerFollowerSnapshotRepository extends JpaRepository<StreamerFollowerSnapshotEntity, Long> {

    Optional<StreamerFollowerSnapshotEntity> findByStreamIdAndSnapshotDate(String streamId, LocalDate snapshotDate);

    List<StreamerFollowerSnapshotEntity> findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
        String streamId,
        LocalDate startDate
    );

    Optional<StreamerFollowerSnapshotEntity> findFirstByStreamIdAndSnapshotDateLessThanOrderBySnapshotDateDesc(
        String streamId,
        LocalDate snapshotDate
    );

    @Modifying
    @Query("DELETE FROM StreamerFollowerSnapshotEntity s WHERE s.snapshotDate < :cutoffDate")
    int deleteExpiredSnapshots(@Param("cutoffDate") LocalDate cutoffDate);
}
