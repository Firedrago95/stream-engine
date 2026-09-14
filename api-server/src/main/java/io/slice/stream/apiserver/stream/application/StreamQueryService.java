package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
public class StreamQueryService {

    public static final String REDIS_STREAM_LIST_KEY = "stream:browser:list";
    private static final Duration CACHE_TTL = Duration.ofSeconds(20);

    private final StreamRepository streamRepository;
    private final AnalysisRepository analysisRepository;
    private final TargetStreamerService targetStreamerService;
    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public List<StreamResponse> getBrowserList(String keyword) {
        boolean isAllSearch = (keyword == null || keyword.isBlank());

        if (isAllSearch) {
            List<StreamResponse> cached = readFromRedis();
            if (!cached.isEmpty()) {
                return cached;
            }
        }

        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        List<StreamEntity> activeStreams;

        List<String> targetIds = targetStreamerService.getActiveTargetChannelIds();
        Set<String> targetIdSet = (targetIds != null) ? new HashSet<>(targetIds) : Collections.emptySet();

        if (!isAllSearch) {
            activeStreams = streamRepository.searchByStreamerName(keyword, threshold);
        } else {
            if (!targetIdSet.isEmpty()) {
                activeStreams = streamRepository.findActiveStreamsByStreamIds(new ArrayList<>(targetIdSet), threshold);
            } else {
                activeStreams = streamRepository.findActiveStreams(threshold);
            }
        }

        Set<String> streamIds = activeStreams.stream()
            .map(StreamEntity::getStreamId)
            .collect(Collectors.toSet());

        Instant signalThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);
        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(streamIds, signalThreshold);

        List<StreamResponse> responses = activeStreams.stream()
            .map(s -> new StreamResponse(
                s.getStreamId(),
                s.getStreamerName(),
                s.getLiveTitle(),
                s.getProfileImageUrl(),
                s.getCategoryName(),
                s.getConcurrentUserCount(),
                StreamStatus.determine(
                    s.isLive() && s.getLastUpdateAt().isAfter(threshold),
                    analyzingIds.contains(s.getStreamId()) || targetIdSet.contains(s.getStreamId()))
            ))
            .toList();

        if (isAllSearch && !responses.isEmpty()) {
            writeToRedis(responses);
        }

        return responses;
    }

    public StreamResponse getStreamInfo(String streamId) {
        StreamEntity s = streamRepository.findById(streamId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_NOT_FOUND, "존재하지 않는 방송입니다."));
        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant signalThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(Set.of(streamId), signalThreshold);
        List<String> targetIds = targetStreamerService.getActiveTargetChannelIds();
        boolean isTarget = targetIds != null && targetIds.contains(streamId);

        return new StreamResponse(
            s.getStreamId(),
            s.getStreamerName(),
            s.getLiveTitle(),
            s.getProfileImageUrl(),
            s.getCategoryName(),
            s.getConcurrentUserCount(),
            StreamStatus.determine(
                s.isLive() && s.getLastUpdateAt().isAfter(threshold),
                analyzingIds.contains(streamId) || isTarget)
        );
    }

    private List<StreamResponse> readFromRedis() {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_STREAM_LIST_KEY);
            if (json == null || json.isBlank()) {
                return Collections.emptyList();
            }
            return jsonMapper.readValue(json, new TypeReference<List<StreamResponse>>() {});
        } catch (Exception e) {
            log.error("[Cache] Redis 메인 방송 목록 조회 실패", e);
            return Collections.emptyList();
        }
    }

    private void writeToRedis(List<StreamResponse> responses) {
        try {
            String json = jsonMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(REDIS_STREAM_LIST_KEY, json, CACHE_TTL);
        } catch (Exception e) {
            log.error("[Cache] Redis 메인 방송 목록 갱신 실패", e);
        }
    }
}
