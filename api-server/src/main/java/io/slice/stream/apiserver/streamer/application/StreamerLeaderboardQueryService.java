package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.domain.StreamStatus;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import io.slice.stream.apiserver.stream.targeting.TargetStreamerService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerLeaderboardQueryService {

    private final StreamRepository streamRepository;
    private final AnalysisRepository analysisRepository;
    private final TargetStreamerService targetStreamerService;

    public List<StreamResponse> getLeaderboard(String keyword) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        List<StreamEntity> streamers = hasKeyword
            ? streamRepository.searchAllStreamersForLeaderboard(keyword.trim())
            : streamRepository.findAllStreamersForLeaderboard();

        if (streamers.isEmpty()) {
            return Collections.emptyList();
        }

        Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
        Instant signalThreshold = Instant.now().minus(5, ChronoUnit.MINUTES);

        Set<String> streamIds = streamers.stream()
            .map(StreamEntity::getStreamId)
            .collect(Collectors.toSet());

        List<String> targetIds = targetStreamerService.getActiveTargetChannelIds();
        Set<String> targetIdSet = targetIds != null ? new HashSet<>(targetIds) : Collections.emptySet();
        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(streamIds, signalThreshold);

        return streamers.stream()
            .map(s -> {
                boolean isLive = s.isLive() && s.getLastUpdateAt().isAfter(threshold);
                StreamStatus status = StreamStatus.determine(
                    isLive,
                    analyzingIds.contains(s.getStreamId()) || targetIdSet.contains(s.getStreamId())
                );
                int displayViewers = isLive ? s.getConcurrentUserCount() : 0;
                return new StreamResponse(
                    s.getStreamId(),
                    s.getStreamerName(),
                    s.getLiveTitle(),
                    s.getProfileImageUrl(),
                    s.getCategoryName(),
                    displayViewers,
                    status
                );
            })
            .toList();
    }
}
