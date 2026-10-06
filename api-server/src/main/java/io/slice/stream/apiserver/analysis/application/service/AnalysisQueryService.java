package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SegmentResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.SessionSummaryResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.TimelineDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionSegmentRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaViewMetricTimelineRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionSegmentEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.ViewMetricTimelineEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long NOISE_THRESHOLD_SECONDS = 300L;

    private final AnalysisRepository analysisRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaViewMetricTimelineRepository timelineRepository;
    private final ReLiveSessionMerger reLiveSessionMerger;

    public List<SessionResponse> getAvailableSessions(String streamId, int limit) {
        int fetchLimit = Math.max(limit * 2, 20);
        List<StreamSessionEntity> sessions = sessionRepository.findRecentValidSessionsByStreamId(
            streamId, 0L, PageRequest.of(0, fetchLimit)
        );

        List<List<StreamSessionEntity>> groups = reLiveSessionMerger.groupSessions(sessions);
        return groups.stream()
            .map(reLiveSessionMerger::mergeToSessionResponse)
            .filter(this::isValidMergedSession)
            .sorted(Comparator.comparing(SessionResponse::startedAt).reversed())
            .limit(limit)
            .toList();
    }

    private boolean isValidMergedSession(SessionResponse session) {
        if (session.endedAt() == null) {
            return true;
        }
        return Duration.between(session.startedAt(), session.endedAt()).getSeconds() >= NOISE_THRESHOLD_SECONDS;
    }

    public AnalysisResponse getHistoryAnalysis(String streamId, String sessionId) {
        List<StreamSessionEntity> linkedGroup = resolveLinkedGroup(streamId, sessionId);
        List<String> linkedSessionIds = linkedGroup.isEmpty()
            ? List.of(sessionId)
            : linkedGroup.stream().map(StreamSessionEntity::getSessionId).toList();
        Instant baseStartedAt = linkedGroup.isEmpty()
            ? null
            : linkedGroup.getFirst().getStartedAt();

        List<SegmentResponse> segmentResponses = fetchSegments(linkedSessionIds, baseStartedAt);

        List<ViewMetricTimelineEntity> timelines = timelineRepository.findBySessionIdInOrderByTimestampAsc(linkedSessionIds);

        SessionSummaryResponse summaryResponse = !linkedGroup.isEmpty()
            ? reLiveSessionMerger.mergeToSessionSummary(linkedGroup)
            : sessionRepository.findBySessionId(sessionId)
                .map(session -> new SessionSummaryResponse(
                    session.getSessionId(),
                    session.getTitle(),
                    session.getCategoryName(),
                    session.getStartedAt(),
                    session.getEndedAt(),
                    session.getPeakViewers(),
                    session.getAverageViewerCount(),
                    session.getSubscriberChatRatio(),
                    session.isAdult()
                ))
                .orElse(null);

        List<AnalysisDataPoint> mergedOneMinutePoints = loadMergedHistoryPoints(streamId, linkedSessionIds, baseStartedAt);
        long bucketIntervalMs = determineBucketIntervalMs(summaryResponse, mergedOneMinutePoints);

        List<AnalysisDataPoint> downsampledPoints = downsampleDataPoints(mergedOneMinutePoints, bucketIntervalMs);
        List<TimelineDataPoint> downsampledTimelines = downsampleTimeline(timelines, bucketIntervalMs);

        return new AnalysisResponse(streamId, downsampledPoints, segmentResponses, downsampledTimelines, summaryResponse);
    }

    private List<StreamSessionEntity> resolveLinkedGroup(String streamId, String sessionId) {
        List<StreamSessionEntity> recentSessions = sessionRepository.findRecentValidSessionsByStreamId(
            streamId, 0L, PageRequest.of(0, 50)
        );
        List<StreamSessionEntity> linked = reLiveSessionMerger.findLinkedGroup(sessionId, recentSessions);
        if (!linked.isEmpty()) {
            return linked;
        }

        return sessionRepository.findBySessionId(sessionId)
            .map(List::of)
            .orElse(List.of());
    }

    private List<AnalysisDataPoint> loadMergedHistoryPoints(String streamId, List<String> sessionIds, Instant baseStartedAt) {
        Map<Long, AnalysisDataPoint> merged = new TreeMap<>();

        for (String sid : sessionIds) {
            List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, sid);
            List<AnalysisDataPoint> rawDataPoints = aggregateToOneMinuteIntervals(analysisRepository.findRawHistory(streamId, sid));

            for (AnalysisDataPoint p : summaryDataPoints) {
                merged.put(p.timestamp(), p);
            }
            for (AnalysisDataPoint p : rawDataPoints) {
                merged.putIfAbsent(p.timestamp(), p);
            }
        }

        if (baseStartedAt == null) {
            return new ArrayList<>(merged.values());
        }

        long baseEpochMs = baseStartedAt.toEpochMilli();
        return merged.values().stream()
            .map(p -> new AnalysisDataPoint(
                p.timestamp(),
                p.value(),
                p.status(),
                Math.max(0L, p.timestamp() - baseEpochMs)
            ))
            .toList();
    }

    private long determineBucketIntervalMs(SessionSummaryResponse summary, List<AnalysisDataPoint> points) {
        long durationMillis = calculateDurationMillis(summary, points);
        long durationHours = durationMillis / 3_600_000L;

        long intervalMinutes;
        if (durationHours <= 2) {
            intervalMinutes = 1;
        } else if (durationHours <= 8) {
            intervalMinutes = 3;
        } else if (durationHours <= 24) {
            intervalMinutes = 10;
        } else if (durationHours <= 72) {
            intervalMinutes = 30;
        } else {
            intervalMinutes = 60;
        }

        return intervalMinutes * ONE_MINUTE_MS;
    }

    private long calculateDurationMillis(SessionSummaryResponse summary, List<AnalysisDataPoint> points) {
        if (summary != null && summary.startedAt() != null) {
            Instant end = summary.endedAt() != null ? summary.endedAt() : Instant.now();
            return Math.max(0L, Duration.between(summary.startedAt(), end).toMillis());
        }
        if (points != null && points.size() >= 2) {
            return Math.max(0L, points.getLast().timestamp() - points.getFirst().timestamp());
        }
        return 0L;
    }

    private List<AnalysisDataPoint> downsampleDataPoints(List<AnalysisDataPoint> points, long bucketIntervalMs) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (bucketIntervalMs <= ONE_MINUTE_MS) {
            return points;
        }

        Map<Long, List<AnalysisDataPoint>> grouped = points.stream()
            .collect(Collectors.groupingBy(
                p -> (p.timestamp() / bucketIntervalMs) * bucketIntervalMs,
                TreeMap::new,
                Collectors.toList()
            ));

        return grouped.entrySet().stream()
            .map(entry -> {
                Long bucketTs = entry.getKey();
                List<AnalysisDataPoint> bucketPoints = entry.getValue();

                long avgValue = (long) bucketPoints.stream()
                    .mapToLong(AnalysisDataPoint::value)
                    .average()
                    .orElse(0.0);

                Long firstOffsetMs = bucketPoints.getFirst().offsetMs();
                String status = bucketPoints.stream()
                    .anyMatch(p -> "PEAK".equals(p.status())) ? "PEAK" : "NORMAL";

                return new AnalysisDataPoint(bucketTs, avgValue, status, firstOffsetMs);
            })
            .toList();
    }

    private List<TimelineDataPoint> downsampleTimeline(List<ViewMetricTimelineEntity> timelines, long bucketIntervalMs) {
        if (timelines == null || timelines.isEmpty()) {
            return List.of();
        }
        if (bucketIntervalMs <= ONE_MINUTE_MS) {
            return timelines.stream()
                .map(t -> new TimelineDataPoint(t.getTimestamp().toEpochMilli(), t.getViewerCount()))
                .toList();
        }

        Map<Long, List<ViewMetricTimelineEntity>> grouped = timelines.stream()
            .collect(Collectors.groupingBy(
                t -> (t.getTimestamp().toEpochMilli() / bucketIntervalMs) * bucketIntervalMs,
                TreeMap::new,
                Collectors.toList()
            ));

        return grouped.entrySet().stream()
            .map(entry -> {
                Long bucketTs = entry.getKey();
                List<ViewMetricTimelineEntity> bucketTimelines = entry.getValue();

                int avgViewers = (int) Math.round(bucketTimelines.stream()
                    .mapToInt(ViewMetricTimelineEntity::getViewerCount)
                    .average()
                    .orElse(0.0));

                return new TimelineDataPoint(bucketTs, avgViewers);
            })
            .toList();
    }

    private List<AnalysisDataPoint> aggregateToOneMinuteIntervals(List<AnalysisDataPoint> rawDataPoints) {
        if (rawDataPoints == null || rawDataPoints.isEmpty()) {
            return List.of();
        }

        Map<Long, List<AnalysisDataPoint>> groupedByMinute = rawDataPoints.stream()
            .collect(Collectors.groupingBy(
                p -> (p.timestamp() / ONE_MINUTE_MS) * ONE_MINUTE_MS,
                TreeMap::new,
                Collectors.toList()
            ));

        return groupedByMinute.entrySet().stream()
            .map(entry -> {
                Long minuteTimestamp = entry.getKey();
                List<AnalysisDataPoint> points = entry.getValue();

                long avgValue = (long) points.stream()
                    .mapToLong(AnalysisDataPoint::value)
                    .average()
                    .orElse(0.0);

                Long firstOffsetMs = points.getFirst().offsetMs();

                String status = points.stream()
                    .anyMatch(p -> "PEAK".equals(p.status())) ? "PEAK" : "NORMAL";

                return new AnalysisDataPoint(minuteTimestamp, avgValue, status, firstOffsetMs);
            })
            .toList();
    }

    private List<SegmentResponse> fetchSegments(List<String> sessionIds, Instant baseStartedAt) {
        List<StreamSessionSegmentEntity> segments = segmentRepository.findBySessionIdInOrderByStartedAtAsc(sessionIds);
        return segments.stream()
            .map(seg -> {
                Long startOffset = seg.getStartOffsetMs();
                Long endOffset = seg.getEndOffsetMs();

                if (baseStartedAt != null && seg.getStartedAt() != null) {
                    startOffset = Math.max(0L, Duration.between(baseStartedAt, seg.getStartedAt()).toMillis());
                    if (seg.getEndedAt() != null) {
                        endOffset = Duration.between(baseStartedAt, seg.getEndedAt()).toMillis();
                    }
                }

                return new SegmentResponse(
                    seg.getId(),
                    seg.getTitle(),
                    seg.getCategoryName(),
                    seg.getStartedAt(),
                    seg.getEndedAt(),
                    startOffset,
                    endOffset,
                    seg.isAdult()
                );
            })
            .toList();
    }
}
