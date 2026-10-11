package io.slice.stream.apiserver.admin.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMetricsService {

    private static final String TARGETS_KEY = "stream:targets";
    private static final String ACTIVE_ANALYSIS_KEY = "analysis:active:channel:ids";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final JpaStreamRepository streamRepository;
    private final JpaHighlightEventRepository highlightRepository;
    private final MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public AdminMetricsOverviewDto getOverview() {
        int targetChannelsCount = getRedisSetSize(TARGETS_KEY);
        int activeAnalyzingCount = getRedisSetSize(ACTIVE_ANALYSIS_KEY);
        long liveStreamsCount = streamRepository.countByIsLiveTrue();

        Instant startOfToday = LocalDate.now(SEOUL_ZONE).atStartOfDay(SEOUL_ZONE).toInstant();
        long todayHighlightCount = highlightRepository.countByStartTimeAfter(startOfToday);

        int hikariActive = getGaugeValue("hikaricp.connections.active");
        int hikariPending = getGaugeValue("hikaricp.connections.pending");
        int hikariIdle = getGaugeValue("hikaricp.connections.idle");

        int kafkaLag = 0;
        String systemStatus = determineSystemStatus(hikariPending, activeAnalyzingCount, liveStreamsCount);

        return new AdminMetricsOverviewDto(
            systemStatus,
            targetChannelsCount,
            activeAnalyzingCount,
            liveStreamsCount,
            todayHighlightCount,
            hikariActive,
            hikariPending,
            hikariIdle,
            kafkaLag
        );
    }

    private int getRedisSetSize(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size != null ? size.intValue() : 0;
        } catch (Exception e) {
            log.warn("[Admin Metrics] Redis 집합 크기 조회 실패: key={}", key);
            return 0;
        }
    }

    private int getGaugeValue(String metricName) {
        try {
            Gauge gauge = meterRegistry.find(metricName).gauge();
            return gauge != null ? (int) gauge.value() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String determineSystemStatus(int hikariPending, int activeAnalyzingCount, long liveStreamsCount) {
        if (hikariPending > 0) {
            return "CRITICAL";
        }
        if (liveStreamsCount > 20 && activeAnalyzingCount == 0) {
            return "WARNING";
        }
        return "ALL_HEALTHY";
    }
}
