package io.slice.stream.apiserver.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import io.slice.stream.apiserver.category.domain.CategoryRepository;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
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
class CategoryQueryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryRankingBatchService batchService;

    private JsonMapper jsonMapper;
    private CategoryQueryService categoryQueryService;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        categoryQueryService = new CategoryQueryService(redisTemplate, jsonMapper, categoryRepository, batchService);
    }

    @Test
    void Redis에_캐시된_주간_랭킹이_있으면_배치_실행_없이_즉시_반환한다() throws Exception {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        List<WeeklyCategoryResponse> cachedResponses = List.of(
            new WeeklyCategoryResponse(1, "메이플스토리", "약 6.6만 시간", 65838L, "up", 2, "🍁"),
            new WeeklyCategoryResponse(2, "마인크래프트", "약 5.8만 시간", 57543L, "same", null, "⛏️")
        );
        String cachedJson = jsonMapper.writeValueAsString(cachedResponses);
        given(valueOps.get(CategoryRankingBatchService.REDIS_WEEKLY_KEY)).willReturn(cachedJson);

        List<WeeklyCategoryResponse> results = categoryQueryService.getWeeklyCategoryRanking();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).categoryName()).isEqualTo("메이플스토리");
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(0).change()).isEqualTo("up");
        assertThat(results.get(0).changeValue()).isEqualTo(2);

        then(categoryRepository).should(never()).findLatestWeeklyRankings();
        then(batchService).should(never()).refreshWeeklyRanking();
    }

    @Test
    void Redis_캐시가_비어있지만_DB에_저장된_랭킹이_있으면_DB에서_조회하여_Redis에_적재하고_반환한다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(CategoryRankingBatchService.REDIS_WEEKLY_KEY)).willReturn(null);

        List<WeeklyCategoryResponse> dbResponses = List.of(
            new WeeklyCategoryResponse(1, "리그 오브 레전드", "약 4.2만 시간", 42171L, "same", null, "⚔️")
        );
        given(categoryRepository.findLatestWeeklyRankings()).willReturn(dbResponses);

        List<WeeklyCategoryResponse> results = categoryQueryService.getWeeklyCategoryRanking();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).categoryName()).isEqualTo("리그 오브 레전드");
        then(valueOps).should().set(eq(CategoryRankingBatchService.REDIS_WEEKLY_KEY), anyString(), eq(Duration.ofDays(8)));
        then(batchService).should(never()).refreshWeeklyRanking();
    }

    @Test
    void Redis_캐시와_DB가_모두_비어있으면_배치_집계를_수행하여_결과를_반환한다() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(CategoryRankingBatchService.REDIS_WEEKLY_KEY)).willReturn(null);
        given(categoryRepository.findLatestWeeklyRankings()).willReturn(Collections.emptyList());

        List<WeeklyCategoryResponse> freshResponses = List.of(
            new WeeklyCategoryResponse(1, "리그 오브 레전드", "약 4.2만 시간", 42171L, "same", null, "⚔️")
        );
        given(batchService.refreshWeeklyRanking()).willReturn(freshResponses);

        List<WeeklyCategoryResponse> results = categoryQueryService.getWeeklyCategoryRanking();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).categoryName()).isEqualTo("리그 오브 레전드");
        then(batchService).should().refreshWeeklyRanking();
    }
}
