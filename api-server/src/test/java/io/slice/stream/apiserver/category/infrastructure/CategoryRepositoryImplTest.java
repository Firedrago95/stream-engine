package io.slice.stream.apiserver.category.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import io.slice.stream.apiserver.category.domain.CategoryViewMetric;
import io.slice.stream.apiserver.category.infrastructure.JpaCategoryRankingRepository.CategoryRankingProjection;
import io.slice.stream.apiserver.category.infrastructure.entity.WeeklyCategoryRankingEntity;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CategoryRepositoryImplTest {

    @Mock
    private JpaCategoryRankingRepository jpaCategoryRankingRepository;

    @Mock
    private JpaWeeklyCategoryRankingRepository jpaWeeklyCategoryRankingRepository;

    @InjectMocks
    private CategoryRepositoryImpl categoryRepository;

    @Captor
    private ArgumentCaptor<List<WeeklyCategoryRankingEntity>> entitiesCaptor;

    @Test
    void 주간_카테고리_랭킹_프로젝션을_도메인_객체로_변환하여_반환한다() {
        CategoryRankingProjection mockProjection = new CategoryRankingProjection() {
            @Override
            public String getCategoryName() {
                return "메이플스토리";
            }

            @Override
            public Long getExactHours() {
                return 65838L;
            }
        };

        given(jpaCategoryRankingRepository.findWeeklyCategoryRankings(any(Instant.class), eq(6)))
            .willReturn(List.of(mockProjection));

        List<CategoryViewMetric> results = categoryRepository.findWeeklyCategoryRankings(Instant.now(), 6);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).categoryName()).isEqualTo("메이플스토리");
        assertThat(results.get(0).exactHours()).isEqualTo(65838L);
    }

    @Test
    void 주간_랭킹_목록을_저장할_때_해당_주의_기존_데이터를_삭제하고_새로_저장한다() {
        LocalDate weekStartDate = LocalDate.of(2026, 3, 9);
        List<WeeklyCategoryResponse> rankings = List.of(
            new WeeklyCategoryResponse(1, "메이플스토리", "약 6.6만 시간", 65838L, "same", null, "🍁")
        );

        categoryRepository.saveAllWeeklyRankings(weekStartDate, rankings);

        then(jpaWeeklyCategoryRankingRepository).should().deleteByWeekStartDate(weekStartDate);
        then(jpaWeeklyCategoryRankingRepository).should().saveAll(entitiesCaptor.capture());

        List<WeeklyCategoryRankingEntity> savedEntities = entitiesCaptor.getValue();
        assertThat(savedEntities).hasSize(1);
        assertThat(savedEntities.get(0).getCategoryName()).isEqualTo("메이플스토리");
        assertThat(savedEntities.get(0).getRank()).isEqualTo(1);
        assertThat(savedEntities.get(0).getWeekStartDate()).isEqualTo(weekStartDate);
    }

    @Test
    void 가장_최근_주차의_주간_랭킹을_조회한다() {
        LocalDate weekStartDate = LocalDate.of(2026, 3, 9);
        given(jpaWeeklyCategoryRankingRepository.findLatestWeekStartDate()).willReturn(Optional.of(weekStartDate));

        WeeklyCategoryRankingEntity entity = new WeeklyCategoryRankingEntity(
            weekStartDate, "마인크래프트", 1, "약 5만 시간", 50000L, "up", 2, "⛏️"
        );
        given(jpaWeeklyCategoryRankingRepository.findByWeekStartDateOrderByRankAsc(weekStartDate))
            .willReturn(List.of(entity));

        List<WeeklyCategoryResponse> results = categoryRepository.findLatestWeeklyRankings();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).categoryName()).isEqualTo("마인크래프트");
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(0).change()).isEqualTo("up");
        assertThat(results.get(0).changeValue()).isEqualTo(2);
    }
}
