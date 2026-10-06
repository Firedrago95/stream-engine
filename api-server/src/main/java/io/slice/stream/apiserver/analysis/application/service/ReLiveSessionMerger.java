package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SessionSummaryResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.global.config.SessionProperties;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse.CalendarSessionDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerSessionHistoryResponse.StreamerSessionItemDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ReLiveSessionMerger {

    private final SessionProperties sessionProperties;

    public ReLiveSessionMerger() {
        this(new SessionProperties(null, null, 50));
    }

    public ReLiveSessionMerger(SessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

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
        return gapSeconds >= 0 && gapSeconds <= sessionProperties.reLiveGap().getSeconds();
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
        List<String> linkedSessionIds = group.stream().map(StreamSessionEntity::getSessionId).toList();

        return new SessionResponse(
            sessionId,
            title,
            categoryName,
            startedAt,
            endedAt,
            peakViewers,
            averageViewerCount,
            subscriberChatRatio,
            isAdult,
            linkedSessionIds
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
        List<String> linkedSessionIds = group.stream().map(StreamSessionEntity::getSessionId).toList();

        return new SessionSummaryResponse(
            sessionId,
            title,
            categoryName,
            startedAt,
            endedAt,
            peakViewers,
            averageViewerCount,
            subscriberChatRatio,
            isAdult,
            linkedSessionIds
        );
    }

    public CalendarSessionDto mergeToCalendarSessionDto(List<StreamSessionEntity> group, Instant now) {
        StreamSessionEntity master = group.getLast();
        StreamSessionEntity first = group.getFirst();

        boolean isLive = group.stream().anyMatch(s -> s.getEndedAt() == null);
        Instant startedAt = first.getStartedAt();
        Instant endedAt = isLive ? null : master.getEndedAt();
        Instant effectiveEndedAt = isLive ? now : endedAt;
        long durationSeconds = Math.max(0L, Duration.between(startedAt, effectiveEndedAt).getSeconds());

        int peakViewers = group.stream()
            .mapToInt(s -> s.getPeakViewers() != null ? s.getPeakViewers() : 0)
            .max()
            .orElse(0);

        Integer averageViewers = calculateMergedAverageViewers(group);

        return new CalendarSessionDto(
            master.getSessionId(),
            master.getTitle() != null ? master.getTitle() : first.getTitle(),
            master.getCategoryName() != null ? master.getCategoryName() : first.getCategoryName(),
            startedAt,
            endedAt,
            durationSeconds,
            peakViewers,
            averageViewers != null ? averageViewers : 0,
            isLive
        );
    }

    public StreamerSessionItemDto mergeToStreamerSessionItemDto(List<StreamSessionEntity> group, Instant now) {
        StreamSessionEntity master = group.getLast();
        StreamSessionEntity first = group.getFirst();

        Instant startedAt = first.getStartedAt();
        Instant endedAt = master.getEndedAt();
        Instant effectiveEndedAt = endedAt != null ? endedAt : now;
        long durationSeconds = Math.max(0L, Duration.between(startedAt, effectiveEndedAt).getSeconds());

        int peakViewers = group.stream()
            .mapToInt(s -> s.getPeakViewers() != null ? s.getPeakViewers() : 0)
            .max()
            .orElse(0);

        Integer averageViewers = calculateMergedAverageViewers(group);
        Double subscriberChatRatio = calculateMergedSubscriberChatRatio(group);
        Integer followerGrowth = group.stream()
            .map(StreamSessionEntity::getSessionFollowerGrowth)
            .filter(Objects::nonNull)
            .reduce(0, Integer::sum);
        boolean paidPromotion = group.stream().anyMatch(StreamSessionEntity::isPaidPromotion);

        return new StreamerSessionItemDto(
            master.getSessionId(),
            master.getTitle() != null ? master.getTitle() : first.getTitle(),
            master.getCategoryName() != null ? master.getCategoryName() : first.getCategoryName(),
            startedAt,
            endedAt,
            durationSeconds,
            peakViewers,
            averageViewers != null ? averageViewers : 0,
            followerGrowth,
            subscriberChatRatio,
            null,
            paidPromotion
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
