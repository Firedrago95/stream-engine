package io.slice.stream.apiserver.category.domain;

import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    List<CategoryViewMetric> findWeeklyCategoryRankings(Instant since, int limit);

    void saveAllWeeklyRankings(LocalDate weekStartDate, List<WeeklyCategoryResponse> rankings);

    List<WeeklyCategoryResponse> findLatestWeeklyRankings();

    Optional<LocalDate> findLatestWeekStartDate();

    List<WeeklyCategoryResponse> findRankingsByWeekStartDate(LocalDate weekStartDate);
}
