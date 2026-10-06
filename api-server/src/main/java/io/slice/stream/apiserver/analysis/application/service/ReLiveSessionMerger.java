package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SessionSummaryResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ReLiveSessionMerger {

    public static final long RE_LIVE_GAP_THRESHOLD_SECONDS = 360L; // 6분

    public List<List<StreamSessionEntity>> groupSessions(List<StreamSessionEntity> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        List<StreamSessionEntity> sorted = sessions.stream()
            .sorted(Comparator.comparing(StreamSessionEntity::getStartedAt))
            .toList();

        List<List<StreamSessionEntity>> groups = new ArrayList<>();
        List<StreamSessionEntity> currentGroup = new ArrayList<>();
        currentGroup.add(sorted.getFirst());

        for (int i = 1; i < sorted.size(); i++) {
            StreamSessionEntity prev = currentGroup.getLast();
            StreamSessionEntity curr = sorted.get(i);

            if (isReLive(prev, curr)) {
                currentGroup.add(curr);
            } else {
                groups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentGroup.add(curr);
            }
        }
        groups.add(currentGroup);
        return groups;
    }

    public boolean isReLive(StreamSessionEntity prev, StreamSessionEntity curr) {
        if (!Objects.equals(prev.getStreamId(), curr.getStreamId())) {
            return false;
        }

        Instant prevAnchor = prev.getEndedAt() != null ? prev.getEndedAt() : prev.getStartedAt();
        long gapSeconds = Duration.between(prevAnchor, curr.getStartedAt()).getSeconds();
        return gapSeconds >= 0 && gapSeconds <= RE_LIVE_GAP_THRESHOLD_SECONDS;
    }

    public List<StreamSessionEntity> findLinkedGroup(String targetSessionId, List<StreamSessionEntity> sessions) {
        if (targetSessionId == null || sessions == null || sessions.isEmpty()) {
            return List.of();
        }

        List<List<StreamSessionEntity>> groups = groupSessions(sessions);
        for (List<StreamSessionEntity> group : groups) {
            boolean contains = group.stream().anyMatch(s -> Objects.equals(s.getSessionId(), targetSessionId));
            if (contains) {
                return group;
            }
        }

        return List.of();
    }

    public List<String> findLinkedSessionIds(String targetSessionId, List<StreamSessionEntity> sessions) {
        List<StreamSessionEntity> linkedGroup = findLinkedGroup(targetSessionId, sessions);
        if (!linkedGroup.isEmpty()) {
            return linkedGroup.stream().map(StreamSessionEntity::getSessionId).toList();
        }

        return targetSessionId != null ? List.of(targetSessionId) : List.of();
    }

    public SessionResponse mergeToSessionResponse(List<StreamSessionEntity> group) {
        StreamSessionEntity master = group.getLast();
        StreamSessionEntity first = group.getFirst();

        String sessionId = master.getSessionId();
        String title = master.getTitle() != null ? master.getTitle() : first.getTitle();
        String categoryName = master.getCategoryName() != null ? master.getCategoryName() : first.getCategoryName();
        Instant startedAt = first.getStartedAt();
        Instant endedAt = master.getEndedAt();

        int peakViewers = group.stream()
            .mapToInt(s -> s.getPeakViewers() != null ? s.getPeakViewers() : 0)
            .max()
            .orElse(0);

        Integer averageViewerCount = calculateMergedAverageViewers(group);
        Double subscriberChatRatio = calculateMergedSubscriberChatRatio(group);
        boolean isAdult = group.stream().anyMatch(StreamSessionEntity::isAdult);

        return new SessionResponse(
            sessionId,
            title,
            categoryName,
            startedAt,
            endedAt,
            peakViewers,
            averageViewerCount,
            subscriberChatRatio,
            isAdult
        );
    }

    public SessionSummaryResponse mergeToSessionSummary(List<StreamSessionEntity> group) {
        StreamSessionEntity master = group.getLast();
        StreamSessionEntity first = group.getFirst();

        String sessionId = master.getSessionId();
        String title = master.getTitle() != null ? master.getTitle() : first.getTitle();
        String categoryName = master.getCategoryName() != null ? master.getCategoryName() : first.getCategoryName();
        Instant startedAt = first.getStartedAt();
        Instant endedAt = master.getEndedAt();

        int peakViewers = group.stream()
            .mapToInt(s -> s.getPeakViewers() != null ? s.getPeakViewers() : 0)
            .max()
            .orElse(0);

        Integer averageViewerCount = calculateMergedAverageViewers(group);
        Double subscriberChatRatio = calculateMergedSubscriberChatRatio(group);
        boolean isAdult = group.stream().anyMatch(StreamSessionEntity::isAdult);

        return new SessionSummaryResponse(
            sessionId,
            title,
            categoryName,
            startedAt,
            endedAt,
            peakViewers,
            averageViewerCount,
            subscriberChatRatio,
            isAdult
        );
    }

    private Integer calculateMergedAverageViewers(List<StreamSessionEntity> group) {
        double totalWeightedViewers = 0.0;
        long totalDurationSeconds = 0L;

        for (StreamSessionEntity s : group) {
            if (s.getAverageViewerCount() != null && s.getAverageViewerCount() > 0) {
                Instant end = s.getEndedAt() != null ? s.getEndedAt() : Instant.now();
                long duration = Math.max(60L, Duration.between(s.getStartedAt(), end).getSeconds());
                totalWeightedViewers += s.getAverageViewerCount() * duration;
                totalDurationSeconds += duration;
            }
        }

        if (totalDurationSeconds > 0) {
            return (int) Math.round(totalWeightedViewers / totalDurationSeconds);
        }

        return group.getLast().getAverageViewerCount();
    }

    private Double calculateMergedSubscriberChatRatio(List<StreamSessionEntity> group) {
        return group.stream()
            .map(StreamSessionEntity::getSubscriberChatRatio)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .stream()
            .boxed()
            .findFirst()
            .orElse(null);
    }
}
