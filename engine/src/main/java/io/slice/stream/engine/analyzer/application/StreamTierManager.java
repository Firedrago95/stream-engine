package io.slice.stream.engine.analyzer.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties;
import io.slice.stream.engine.analyzer.application.config.HighlightEngineProperties.GroupProperties;
import io.slice.stream.engine.analyzer.domain.aggregation.ChatRoomAggregationRepository;
import io.slice.stream.engine.analyzer.domain.stream.ActiveStreamProvider;
import io.slice.stream.engine.analyzer.domain.tier.StreamTier;
import io.slice.stream.engine.analyzer.domain.tier.StreamTierInfo;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamTierManager {

    private final ActiveStreamProvider activeStreamProvider;
    private final ChatRoomAggregationRepository chatRepository;
    private final HighlightEngineProperties props;

    private final Cache<String, StreamTierInfo> tierCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(2000)
        .build();

    public StreamTierInfo getTierInfo(String streamId, int currentViewers) {
        StreamTierInfo info = tierCache.getIfPresent(streamId);
        if (info != null) {
            return info;
        }
        return createColdStartTier(streamId, currentViewers);
    }

    @Scheduled(fixedRateString = "${highlight.engine.manager-refresh-ms}")
    public void refreshAllTiers() {
        List<String> activeStreamIds = activeStreamProvider.getActiveStreamIds();
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        for (String streamId : activeStreamIds) {
            try {
                processTierUpdate(streamId, oneHourAgo, now);
            } catch (Exception e) {
                log.error("[TierManager] 스트림 {} 체급 갱신 중 에러 발생", streamId, e);
            }
        }
    }

    private void processTierUpdate(String streamId, Instant from, Instant to) {
        List<Long> lastHourDeltas = chatRepository.getFirepowerDeltas(streamId, from, to);
        if (lastHourDeltas.size() < 100) return;

        // 최근 60분 데이터를 기반으로 동적 임계치 및 체급 산출
        long percentile1Cutoff = calculatePercentile(lastHourDeltas, props.percentileCut());
        StreamTier tier = determineTier(lastHourDeltas);

        tierCache.put(streamId, buildTierInfo(streamId, tier, percentile1Cutoff));
    }

    private StreamTier determineTier(List<Long> deltas) {
        // 최근 30분간의 평균 및 피크 화력을 승급 조건과 비교
        List<Long> last30MinDeltas = extractRecentHalf(deltas);
        double avg30Min = last30MinDeltas.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long max30Min = last30MinDeltas.stream().mapToLong(Long::longValue).max().orElse(0L);

        if (avg30Min >= props.tier().groupA().conditionMinAvg() ||
            max30Min >= props.tier().groupA().conditionMinPeak()) {
            return StreamTier.GROUP_A;
        }
        return StreamTier.GROUP_B;
    }

    private StreamTierInfo buildTierInfo(String streamId, StreamTier tier, long cutoff) {
        GroupProperties groupProps = (tier == StreamTier.GROUP_A) ? props.tier().groupA() : props.tier().groupB();

        return StreamTierInfo.builder()
            .streamId(streamId)
            .tier(tier)
            .minFirepowerCutoff(cutoff)
            .windowSeconds(groupProps.windowSeconds())
            .zScoreThreshold(groupProps.zScore())
            .maskingExclusionTicks(props.getMaskingTickCount())
            .build();
    }

    private StreamTierInfo createColdStartTier(String streamId, int currentViewers) {
        // 방송 초기 데이터 부족 시 시청자 수 기반 임시 임계치 생성
        long calculatedCutoff = (long) (currentViewers * props.coldStartWeight());
        long hardFloorCutoff = Math.max(10L, calculatedCutoff);

        return buildTierInfo(streamId, StreamTier.GROUP_B, hardFloorCutoff);
    }

    private long calculatePercentile(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private List<Long> extractRecentHalf(List<Long> allDeltas) {
        int half = allDeltas.size() / 2;
        return allDeltas.subList(half, allDeltas.size());
    }
}
