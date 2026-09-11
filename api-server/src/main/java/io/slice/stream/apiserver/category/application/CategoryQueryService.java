package io.slice.stream.apiserver.category.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.slice.stream.apiserver.category.domain.CategoryRepository;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;
    private final CategoryRankingBatchService batchService;

    public List<WeeklyCategoryResponse> getWeeklyCategoryRanking() {
        List<WeeklyCategoryResponse> cached = readFromRedis();
        if (!cached.isEmpty()) {
            return cached;
        }

        List<WeeklyCategoryResponse> fromDb = categoryRepository.findLatestWeeklyRankings();
        if (!fromDb.isEmpty()) {
            writeToRedis(fromDb);
            return fromDb;
        }

        return batchService.refreshWeeklyRanking();
    }

    private List<WeeklyCategoryResponse> readFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(CategoryRankingBatchService.REDIS_WEEKLY_KEY);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, new TypeReference<List<WeeklyCategoryResponse>>() {});
        } catch (Exception e) {
            log.error("[Cache] Redis 주간 카테고리 랭킹 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private void writeToRedis(List<WeeklyCategoryResponse> rankings) {
        try {
            String json = objectMapper.writeValueAsString(rankings);
            redisTemplate.opsForValue().set(CategoryRankingBatchService.REDIS_WEEKLY_KEY, json);
        } catch (Exception e) {
            log.error("[Cache] Redis 주간 카테고리 랭킹 갱신 실패", e);
        }
    }
}
