package io.slice.stream.apiserver.stream.application;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.stream.domain.StreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.presentation.dto.StreamResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamQueryService {

    private final StreamRepository streamRepository;
    private final AnalysisRepository analysisRepository;

    public List<StreamResponse> getBrowserList(String keyword) {
        List<StreamEntity> activeStreams;

        if (keyword != null && !keyword.isBlank()) {
            activeStreams = streamRepository.findByStreamerNameContainingIgnoreCase(keyword);
        } else {
            Instant threshold = Instant.now().minus(3, ChronoUnit.MINUTES);
            activeStreams = streamRepository.findActiveStreams(threshold);
        }

        Set<String> streamIds = activeStreams.stream()
            .map(StreamEntity::getStreamId)
            .collect(Collectors.toSet());

        Set<String> analyzingIds = analysisRepository.findChannelsWithRecentSignals(streamIds);

        return activeStreams.stream()
            .map(s -> new StreamResponse(
                s.getStreamId(),
                s.getStreamerName(),
                s.getLiveTitle(),
                s.getProfileImageUrl(),
                s.getCategoryName(),
                analyzingIds.contains(s.getStreamId()) ? "ANALYZING" : "LIVE"
            ))
            .toList();
    }
}
