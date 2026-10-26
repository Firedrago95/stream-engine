package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.infrastructure.JpaHighlightEventRepository;
import io.slice.stream.apiserver.analysis.infrastructure.entity.HighlightEventEntity;
import io.slice.stream.apiserver.analysis.presentation.dto.HighlightResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.RecentHighlightSessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.global.config.SessionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class HighlightQueryService {

    private final JpaHighlightEventRepository highlightRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final ReLiveSessionMerger reLiveSessionMerger;
    private final SessionProperties sessionProperties;

    public HighlightQueryService(
        JpaHighlightEventRepository highlightRepository,
        JpaStreamSessionRepository sessionRepository,
        ReLiveSessionMerger reLiveSessionMerger
    ) {
        this(highlightRepository, sessionRepository, reLiveSessionMerger, new SessionProperties(null, null, 50));
    }

    @Autowired
    public HighlightQueryService(
        JpaHighlightEventRepository highlightRepository,
        JpaStreamSessionRepository sessionRepository,
        ReLiveSessionMerger reLiveSessionMerger,
        SessionProperties sessionProperties
    ) {
        this.highlightRepository = highlightRepository;
        this.sessionRepository = sessionRepository;
        this.reLiveSessionMerger = reLiveSessionMerger;
        this.sessionProperties = sessionProperties;
    }

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
            .sorted(this::compareHighlights)
            .map(h -> HighlightResponse.from(h, baseStartedAt))
            .toList();
    }

    public List<RecentHighlightSessionResponse> getRecentFinishedSessions(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<Object[]> rows = sessionRepository.findRecentFinishedSessionsWithStreamerName(PageRequest.of(0, boundedLimit))
            .getContent();

        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> sessionIds = rows.stream()
            .map(r -> ((StreamSessionEntity) r[0]).getSessionId())
            .toList();

        Map<String, Long> countMap = highlightRepository.countGroupedBySessionIdIn(sessionIds).stream()
            .collect(Collectors.toMap(
                r -> (String) r[0],
                r -> ((Number) r[1]).longValue(),
                (a, b) -> a
            ));

        return rows.stream()
            .map(row -> mapToRecentSessionResponse(row, countMap))
            .toList();
    }

    private RecentHighlightSessionResponse mapToRecentSessionResponse(Object[] row, Map<String, Long> countMap) {
        StreamSessionEntity session = (StreamSessionEntity) row[0];
        String streamerName = (String) row[1];
        long duration = (session.getStartedAt() != null && session.getEndedAt() != null)
            ? Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds()
            : 0L;
        long hlCount = countMap.getOrDefault(session.getSessionId(), 0L);

        return new RecentHighlightSessionResponse(
            session.getStreamId(),
            streamerName != null ? streamerName : "스트리머",
            session.getSessionId(),
            session.getTitle(),
            session.getCategoryName(),
            session.getStartedAt(),
            session.getEndedAt(),
            duration,
            session.getPeakViewers() != null ? session.getPeakViewers() : 0,
            session.getAverageViewerCount() != null ? session.getAverageViewerCount().intValue() : 0,
            hlCount
        );
    }

    private int compareHighlights(HighlightEventEntity a, HighlightEventEntity b) {
        if (a.getRankInSession() != null && b.getRankInSession() != null) {
            return a.getRankInSession().compareTo(b.getRankInSession());
        }
        if (a.getRankInSession() != null) {
            return -1;
        }
        if (b.getRankInSession() != null) {
            return 1;
        }
        return b.getPeakFirepower().compareTo(a.getPeakFirepower());
    }

    private List<StreamSessionEntity> resolveLinkedGroup(String streamId, String targetSessionId) {
        List<StreamSessionEntity> recentSessions = sessionRepository.findRecentValidSessionsByStreamId(
            streamId, 0L, PageRequest.of(0, sessionProperties.recentFetchLimit())
        );
        return reLiveSessionMerger.findLinkedGroup(targetSessionId, recentSessions);
    }
}

