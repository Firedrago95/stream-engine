package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HighlightQueryService {

    private final JpaHighlightEventRepository highlightRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final ReLiveSessionMerger reLiveSessionMerger;

    public List<HighlightResponse> getHighlightsBySessionId(String streamId, String sessionId) {
        String targetSessionId = sessionId;
        if (targetSessionId == null || targetSessionId.equals("realtime")) {
            targetSessionId = sessionRepository.findActiveSession(streamId)
                .map(StreamSessionEntity::getSessionId)
                .orElse(null);
        }

        if (targetSessionId == null) {
            return List.of();
        }

        List<StreamSessionEntity> linkedGroup = resolveLinkedGroup(streamId, targetSessionId);
        List<String> targetSessionIds = linkedGroup.isEmpty()
            ? List.of(targetSessionId)
            : linkedGroup.stream().map(StreamSessionEntity::getSessionId).toList();
        Instant baseStartedAt = linkedGroup.isEmpty() ? null : linkedGroup.getFirst().getStartedAt();

        return highlightRepository.findAllByStreamIdAndSessionIdIn(streamId, targetSessionIds)
            .stream()
            .sorted((a, b) -> b.getPeakFirepower().compareTo(a.getPeakFirepower()))
            .map(h -> HighlightResponse.from(h, baseStartedAt))
            .toList();
    }

    private List<StreamSessionEntity> resolveLinkedGroup(String streamId, String targetSessionId) {
        List<StreamSessionEntity> recentSessions = sessionRepository.findRecentValidSessionsByStreamId(
            streamId, 0L, PageRequest.of(0, 50)
        );
        return reLiveSessionMerger.findLinkedGroup(targetSessionId, recentSessions);
    }
}

