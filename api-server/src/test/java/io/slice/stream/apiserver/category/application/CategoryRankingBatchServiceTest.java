package io.slice.stream.apiserver.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import io.slice.stream.apiserver.category.domain.CategoryRepository;
import io.slice.stream.apiserver.category.domain.CategoryViewMetric;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CategoryRankingBatchServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    private JsonMapper jsonMapper;
    private CategoryRankingBatchService batchService;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        batchService = new CategoryRankingBatchService(categoryRepository, redisTemplate, jsonMapper, 30);
    }

    @Test
    void 지난주_순위와_비교하여_순위_상승_하락_유지_신규_진입을_정확히_계산하고_DB와_Redis에_저장한다() throws Exception {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        LocalDate lastWeek = LocalDate.now(ZoneId.of("Asia/Seoul"))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(1);
        given(categoryRepository.findLatestWeekStartDate()).willReturn(Optional.of(lastWeek));

        List<WeeklyCategoryResponse> previousRankings = List.of(
            new WeeklyCategoryResponse(1, "마인크래프트", "약 5만 시간", 50000L, "same", null, "⛏️"),
            new WeeklyCategoryResponse(3, "메이플스토리", "약 3만 시간", 30000L, "same", null, "🍁"),
            new WeeklyCategoryResponse(3, "리그 오브 레전드", "약 3만 시간", 30000L, "same", null, "⚔️")
        );
        given(categoryRepository.findRankingsByWeekStartDate(lastWeek)).willReturn(previousRankings);

        List<CategoryViewMetric> currentMetrics = List.of(
            new CategoryViewMetric("메이플스토리", 65838L),
            new CategoryViewMetric("신규게임", 50000L),
            new CategoryViewMetric("리그 오브 레전드", 42171L),
            new CategoryViewMetric("마인크래프트", 25000L)
        );
        given(categoryRepository.findWeeklyCategoryRankings(any(Instant.class), eq(120.0), eq(6)))
            .willReturn(currentMetrics);

        List<WeeklyCategoryResponse> results = batchService.refreshWeeklyRanking();

        assertThat(results).hasSize(4);

        WeeklyCategoryResponse rank1 = results.get(0);
        assertThat(rank1.categoryName()).isEqualTo("메이플스토리");
        assertThat(rank1.rank()).isEqualTo(1);
        assertThat(rank1.change()).isEqualTo("up");
        assertThat(rank1.changeValue()).isEqualTo(2);

        WeeklyCategoryResponse rank2 = results.get(1);
        assertThat(rank2.categoryName()).isEqualTo("신규게임");
        assertThat(rank2.rank()).isEqualTo(2);
        assertThat(rank2.change()).isEqualTo("new");
        assertThat(rank2.changeValue()).isNull();

        WeeklyCategoryResponse rank3 = results.get(2);
        assertThat(rank3.categoryName()).isEqualTo("리그 오브 레전드");
        assertThat(rank3.rank()).isEqualTo(3);
        assertThat(rank3.change()).isEqualTo("same");
        assertThat(rank3.changeValue()).isNull();

        WeeklyCategoryResponse rank4 = results.get(3);
        assertThat(rank4.categoryName()).isEqualTo("마인크래프트");
        assertThat(rank4.rank()).isEqualTo(4);
        assertThat(rank4.change()).isEqualTo("down");
        assertThat(rank4.changeValue()).isEqualTo(3);

        then(categoryRepository).should().saveAllWeeklyRankings(any(LocalDate.class), eq(results));
        then(valueOps).should().set(eq(CategoryRankingBatchService.REDIS_WEEKLY_KEY), anyString());
    }

    @Test
    void 이전_스냅샷이_없는_최초_실행_시에는_same_상태로_초기화된다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(categoryRepository.findLatestWeekStartDate()).willReturn(Optional.empty());

        List<CategoryViewMetric> currentMetrics = List.of(
            new CategoryViewMetric("메이플스토리", 65838L)
        );
        given(categoryRepository.findWeeklyCategoryRankings(any(Instant.class), eq(120.0), eq(6)))
            .willReturn(currentMetrics);

        List<WeeklyCategoryResponse> results = batchService.refreshWeeklyRanking();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).change()).isEqualTo("same");
        assertThat(results.get(0).changeValue()).isNull();

        then(categoryRepository).should().saveAllWeeklyRankings(any(LocalDate.class), eq(results));
        then(valueOps).should().set(eq(CategoryRankingBatchService.REDIS_WEEKLY_KEY), anyString());
    }
}
