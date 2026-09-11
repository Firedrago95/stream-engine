package io.slice.stream.apiserver.category.infrastructure;

import io.slice.stream.apiserver.category.infrastructure.entity.WeeklyCategoryRankingEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWeeklyCategoryRankingRepository extends JpaRepository<WeeklyCategoryRankingEntity, Long> {

    @Query("SELECT MAX(w.weekStartDate) FROM WeeklyCategoryRankingEntity w")
    Optional<LocalDate> findLatestWeekStartDate();

    List<WeeklyCategoryRankingEntity> findByWeekStartDateOrderByRankAsc(LocalDate weekStartDate);

    @Modifying
    @Query("DELETE FROM WeeklyCategoryRankingEntity w WHERE w.weekStartDate = :weekStartDate")
    void deleteByWeekStartDate(@Param("weekStartDate") LocalDate weekStartDate);
}
