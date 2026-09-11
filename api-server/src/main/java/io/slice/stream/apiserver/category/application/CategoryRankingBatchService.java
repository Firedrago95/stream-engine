package io.slice.stream.apiserver.category.application;

import io.slice.stream.apiserver.category.domain.CategoryRepository;
import io.slice.stream.apiserver.category.domain.CategoryViewMetric;
import io.slice.stream.apiserver.category.presentation.dto.WeeklyCategoryResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class CategoryRankingBatchService {

    public static final String REDIS_WEEKLY_KEY = "category:ranking:weekly";
    private static final int DEFAULT_TOP_LIMIT = 6;
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final Map<String, String> ICON_MAP = Map.ofEntries(
        Map.entry("talk", "💬"),
        Map.entry("소통/토크", "💬"),
        Map.entry("음악/노래", "🎤"),
        Map.entry("메이플스토리", "🍁"),
        Map.entry("마인크래프트", "⛏️"),
        Map.entry("종합 게임", "🎮"),
        Map.entry("리그 오브 레전드", "⚔️"),
        Map.entry("오버워치", "🎯"),
        Map.entry("이터널 리턴", "🧪"),
        Map.entry("로스트아크", "🛡️"),
        Map.entry("발로란트", "🎯"),
        Map.entry("PUBG: 배틀그라운드", "🪂")
    );

    private final CategoryRepository categoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final int timelineIntervalSeconds;

    public CategoryRankingBatchService(
        CategoryRepository categoryRepository,
        StringRedisTemplate redisTemplate,
        JsonMapper jsonMapper,
        @Value("${category.ranking.timeline-interval-seconds:30}") int timelineIntervalSeconds
    ) {
        this.categoryRepository = categoryRepository;
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.timelineIntervalSeconds = timelineIntervalSeconds;
    }

    public List<WeeklyCategoryResponse> refreshWeeklyRanking() {
        log.info("[Batch] 주간 인기 카테고리 랭킹 집계 시작");
        LocalDate currentMonday = LocalDate.now(KST_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        double samplesPerHour = 3600.0 / Math.max(1, timelineIntervalSeconds);

        List<CategoryViewMetric> currentMetrics = categoryRepository.findWeeklyCategoryRankings(since, samplesPerHour, DEFAULT_TOP_LIMIT);
        Map<String, Integer> previousRanks = loadPreviousRanks(currentMonday);
        boolean isInitialSnapshot = previousRanks.isEmpty();

        List<WeeklyCategoryResponse> responses = new ArrayList<>();

        for (int i = 0; i < currentMetrics.size(); i++) {
            CategoryViewMetric metric = currentMetrics.get(i);
            int currentRank = i + 1;
            String categoryName = metric.categoryName();

            String change;
            Integer changeValue = null;

            if (isInitialSnapshot) {
                change = "same";
            } else if (!previousRanks.containsKey(categoryName)) {
                change = "new";
            } else {
                int prevRank = previousRanks.get(categoryName);
                if (currentRank < prevRank) {
                    change = "up";
                    changeValue = prevRank - currentRank;
                } else if (currentRank > prevRank) {
                    change = "down";
                    changeValue = currentRank - prevRank;
                } else {
                    change = "same";
                }
            }

            String formattedHours = formatViewHours(metric.exactHours());
            String icon = resolveIcon(categoryName);

            responses.add(new WeeklyCategoryResponse(
                currentRank,
                categoryName,
                formattedHours,
                metric.exactHours(),
                change,
                changeValue,
                icon
            ));
        }

        categoryRepository.saveAllWeeklyRankings(currentMonday, responses);
        saveToRedis(responses);

        log.info("[Batch] 주간 인기 카테고리 랭킹 집계 및 DB/Redis 저장 완료 (카테고리 수: {})", responses.size());
        return responses;
    }

    private Map<String, Integer> loadPreviousRanks(LocalDate currentMonday) {
        Optional<LocalDate> latestWeekOpt = categoryRepository.findLatestWeekStartDate();
        if (latestWeekOpt.isEmpty()) {
            return Map.of();
        }

        LocalDate latestDate = latestWeekOpt.get();
        if (latestDate.isEqual(currentMonday)) {
            LocalDate previousMonday = currentMonday.minusWeeks(1);
            return extractRankMap(categoryRepository.findRankingsByWeekStartDate(previousMonday));
        }

        return extractRankMap(categoryRepository.findRankingsByWeekStartDate(latestDate));
    }

    private Map<String, Integer> extractRankMap(List<WeeklyCategoryResponse> rankings) {
        Map<String, Integer> rankMap = new HashMap<>();
        for (WeeklyCategoryResponse r : rankings) {
            rankMap.put(r.categoryName(), r.rank());
        }
        return rankMap;
    }

    private void saveToRedis(List<WeeklyCategoryResponse> responses) {
        try {
            String weeklyJson = jsonMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(REDIS_WEEKLY_KEY, weeklyJson);
        } catch (Exception e) {
            log.error("[Batch] Redis 카테고리 랭킹 캐시 저장 실패", e);
        }
    }

    private String formatViewHours(long exactHours) {
        if (exactHours >= 10_000) {
            double manUnit = exactHours / 10000.0;
            return String.format("약 %.1f만 시간", manUnit);
        }
        return String.format("약 %,d시간", exactHours);
    }

    private String resolveIcon(String categoryName) {
        if (categoryName == null) {
            return "🎮";
        }
        return ICON_MAP.getOrDefault(categoryName, "🎮");
    }
}
