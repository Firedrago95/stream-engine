package io.slice.stream.apiserver.category.infrastructure;

import io.slice.stream.apiserver.category.domain.CategoryRepository;
import io.slice.stream.apiserver.category.domain.CategoryViewMetric;
import io.slice.stream.apiserver.category.infrastructure.entity.WeeklyCategoryRankingEntity;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final JpaCategoryRankingRepository jpaCategoryRankingRepository;
    private final JpaWeeklyCategoryRankingRepository jpaWeeklyCategoryRankingRepository;

    @Override
    public List<CategoryViewMetric> findWeeklyCategoryRankings(Instant since, double samplesPerHour, int limit) {
        return jpaCategoryRankingRepository.findWeeklyCategoryRankings(since, samplesPerHour, limit)
            .stream()
            .map(p -> new CategoryViewMetric(p.getCategoryName(), p.getExactHours()))
            .toList();
    }

    @Override
    @Transactional
    public void saveAllWeeklyRankings(LocalDate weekStartDate, List<WeeklyCategoryResponse> rankings) {
        jpaWeeklyCategoryRankingRepository.deleteByWeekStartDate(weekStartDate);

        List<WeeklyCategoryRankingEntity> entities = rankings.stream()
            .map(r -> new WeeklyCategoryRankingEntity(
                weekStartDate,
                r.categoryName(),
                r.rank(),
                r.accumulatedViewHours(),
                r.exactHours(),
                r.change(),
                r.changeValue(),
                r.icon()
            ))
            .toList();

        jpaWeeklyCategoryRankingRepository.saveAll(entities);
    }

    @Override
    public List<WeeklyCategoryResponse> findLatestWeeklyRankings() {
        return jpaWeeklyCategoryRankingRepository.findLatestWeekStartDate()
            .map(this::findRankingsByWeekStartDate)
            .orElse(Collections.emptyList());
    }

    @Override
    public Optional<LocalDate> findLatestWeekStartDate() {
        return jpaWeeklyCategoryRankingRepository.findLatestWeekStartDate();
    }

    @Override
    public List<WeeklyCategoryResponse> findRankingsByWeekStartDate(LocalDate weekStartDate) {
        return jpaWeeklyCategoryRankingRepository.findByWeekStartDateOrderByRankAsc(weekStartDate)
            .stream()
            .map(e -> new WeeklyCategoryResponse(
                e.getRank(),
                e.getCategoryName(),
                e.getAccumulatedViewHours(),
                e.getExactHours(),
                e.getChangeType(),
                e.getChangeValue(),
                e.getIcon()
            ))
            .toList();
    }
}
