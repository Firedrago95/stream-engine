package io.slice.stream.apiserver.streamer.infrastructure;

import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerDailyStatEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStreamerDailyStatRepository extends JpaRepository<StreamerDailyStatEntity, Long> {

    @Query("SELECT s FROM StreamerDailyStatEntity s " +
           "WHERE s.channelId = :channelId " +
           "AND s.statDate BETWEEN :startDate AND :endDate " +
           "ORDER BY s.statDate ASC")
    List<StreamerDailyStatEntity> findByChannelIdAndDateRange(
        @Param("channelId") String channelId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    Optional<StreamerDailyStatEntity> findByChannelIdAndStatDate(String channelId, LocalDate statDate);

    @Query("SELECT s.statDate FROM StreamerDailyStatEntity s " +
           "WHERE s.channelId = :channelId " +
           "AND s.statDate <= :today " +
           "AND s.broadcastDurationSeconds > 0 " +
           "ORDER BY s.statDate DESC")
    List<LocalDate> findActiveDatesUpTo(
        @Param("channelId") String channelId,
        @Param("today") LocalDate today,
        Pageable pageable
    );
}
