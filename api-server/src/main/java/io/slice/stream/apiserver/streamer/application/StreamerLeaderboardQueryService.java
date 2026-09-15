package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerLeaderboardProjection;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerLeaderboardQueryService {

    public static final String REDIS_LEADERBOARD_KEY = "streamer:leaderboard:daily:top100";
    private static final Duration CACHE_TTL = Duration.ofHours(36);
    private static final int DEFAULT_TOP_LIMIT = 100;
    private static final int DAYS_30 = 30;
    private static final int MIN_SESSIONS = 5;

    private final StreamRepository streamRepository;
    private final AnalysisRepository analysisRepository;
    private final TargetStreamerService targetStreamerService;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public List<StreamResponse> getLeaderboard(String keyword) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<StreamResponse> cached = readFromRedis();
        boolean cacheRefreshed = false;
        if (cached.isEmpty()) {
            cached = refreshDailyLeaderboard();
            cacheRefreshed = true;
        }

        if (hasKeyword) {
            String trimmed = keyword.trim().toLowerCase();
            List<StreamResponse> matchedInCache = cached.stream()
                .filter(item -> item.streamerName() != null && item.streamerName().toLowerCase().contains(trimmed))
                .toList();

            if (!matchedInCache.isEmpty()) {
                return cacheRefreshed ? matchedInCache : syncRealtimeStatusForCached(matchedInCache);
            }

            Instant since = Instant.now().minus(DAYS_30, ChronoUnit.DAYS);
            List<StreamerLeaderboardProjection> searched =
                streamRepository.searchTopStreamersWith30dAvg(keyword.trim(), since, 50);
            return bindRealtimeLiveStatus(searched);
        }

        return cacheRefreshed ? cached : syncRealtimeStatusForCached(cached);
    }

    @Transactional
    public List<StreamResponse> refreshDailyLeaderboard() {
        Instant since = Instant.now().minus(DAYS_30, ChronoUnit.DAYS);
        List<StreamerLeaderboardProjection> topStreamers =
            streamRepository.findTopStreamersWith30dAvg(since, MIN_SESSIONS, DEFAULT_TOP_LIMIT);

        List<StreamResponse> calculated = bindRealtimeLiveStatus(topStreamers);
        if (!calculated.isEmpty()) {
            writeToRedis(calculated);
        }

        log.info("[Leaderboard] 일일 스트리머 순수 체급 리더보드 정산 완료 (총 {}명)", calculated.size());
        return calculated;
    }

    private List<StreamResponse> bindRealtimeLiveStatus(List<StreamerLeaderboardProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }

        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant signalThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        Set<String> streamIds = projections.stream()
            .map(StreamerLeaderboardProjection::getStreamId)
            .collect(Collectors.toSet());

        List<String> targetIds = targetStreamerService.getActiveTargetChannelIds();
        Set<String> targetIdSet = targetIds != null ? new HashSet<>(targetIds) : Collections.emptySet();
        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(streamIds, signalThreshold);

        return projections.stream()
            .map(p -> {
                boolean isLive = p.getIsLive() && p.getLastUpdateAt() != null && p.getLastUpdateAt().isAfter(threshold);
                StreamStatus status = StreamStatus.determine(
                    isLive,
                    analyzingIds.contains(p.getStreamId()) || targetIdSet.contains(p.getStreamId())
                );
                int displayViewers = isLive ? p.getConcurrentUserCount() : 0;
                return new StreamResponse(
                    p.getStreamId(),
                    p.getStreamerName(),
                    p.getLiveTitle(),
                    p.getProfileImageUrl(),
                    p.getCategoryName(),
                    displayViewers,
                    status,
                    p.getAverageViewers()
                );
            })
            .toList();
    }

    private List<StreamResponse> syncRealtimeStatusForCached(List<StreamResponse> cached) {
        if (cached == null || cached.isEmpty()) {
            return Collections.emptyList();
        }

        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant signalThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        List<String> streamIds = cached.stream()
            .map(StreamResponse::streamId)
            .toList();

        List<StreamEntity> currentEntities = streamRepository.findActiveStreamsByStreamIds(streamIds, threshold);
        Map<String, StreamEntity> activeEntityMap = currentEntities.stream()
            .collect(Collectors.toMap(StreamEntity::getStreamId, s -> s, (a, b) -> a));

        List<String> targetIds = targetStreamerService.getActiveTargetChannelIds();
        Set<String> targetIdSet = targetIds != null ? new HashSet<>(targetIds) : Collections.emptySet();
        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(new HashSet<>(streamIds), signalThreshold);

        return cached.stream()
            .map(item -> {
                StreamEntity active = activeEntityMap.get(item.streamId());
                boolean isLive = active != null && active.isLive() && active.getLastUpdateAt().isAfter(threshold);
                StreamStatus status = StreamStatus.determine(
                    isLive,
                    analyzingIds.contains(item.streamId()) || targetIdSet.contains(item.streamId())
                );
                int liveViewers = (isLive && active != null) ? active.getConcurrentUserCount() : 0;
                String liveTitle = (active != null && active.getLiveTitle() != null) ? active.getLiveTitle() : item.liveTitle();

                return new StreamResponse(
                    item.streamId(),
                    item.streamerName(),
                    liveTitle,
                    item.profileImageUrl(),
                    item.categoryName(),
                    liveViewers,
                    status,
                    item.averageViewers()
                );
            })
            .toList();
    }

    private List<StreamResponse> readFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_LEADERBOARD_KEY);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return jsonMapper.readValue(json, new TypeReference<List<StreamResponse>>() {});
        } catch (Exception e) {
            log.error("[Cache] Redis 일일 리더보드 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private void writeToRedis(List<StreamResponse> responses) {
        try {
            String json = jsonMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(REDIS_LEADERBOARD_KEY, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("[Cache] Redis 일일 리더보드 갱신 실패", e);
        }
    }
}
