package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
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

    private static final int FIND_LIMIT = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long ONE_MINUTE_MS = 60_000L;

    private final AnalysisRepository analysisRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final JpaStreamSessionSegmentRepository segmentRepository;
    private final JpaViewMetricTimelineRepository timelineRepository;

    public AnalysisResponse getRecentAnalysis(String streamId) {
        List<AnalysisSignal> signals = analysisRepository.findRecentSignals(streamId, FIND_LIMIT);

        List<AnalysisDataPoint> dataPoints = signals.stream()
            .map(s -> new AnalysisDataPoint(
                s.timestamp().toEpochMilli(),
                s.firepower(),
                s.status()
            ))
            .toList();

        Optional<StreamSessionEntity> activeSession = sessionRepository.findActiveSession(streamId);
        List<TimelineDataPoint> timeline = activeSession
            .map(s -> timelineRepository.findBySessionIdOrderByTimestampAsc(s.getSessionId()).stream()
                .map(t -> new TimelineDataPoint(t.getTimestamp().toEpochMilli(), t.getViewerCount()))
                .toList())
            .orElse(List.of());

        SessionSummaryResponse summary = activeSession
            .map(s -> new SessionSummaryResponse(
                s.getSessionId(),
                s.getTitle(),
                s.getCategoryName(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getPeakViewers(),
                s.getAverageViewerCount(),
                s.getSubscriberChatRatio()
            ))
            .orElse(null);

        return new AnalysisResponse(streamId, dataPoints, List.of(), timeline, summary);
    }

    public List<SessionResponse> getAvailableSessions(String streamId, int limit) {
        return sessionRepository.findRecentSessionsByStreamId(streamId, PageRequest.of(0, limit))
            .stream()
            .map(session -> new SessionResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getCategoryName(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPeakViewers(),
                session.getAverageViewerCount(),
                session.getSubscriberChatRatio()
            ))
            .toList();
    }

    public AnalysisResponse getHistoryAnalysis(String streamId, String sessionId) {
        List<StreamSessionSegmentEntity> segments = segmentRepository.findBySessionIdOrderByStartedAtAsc(sessionId);
        List<SegmentResponse> segmentResponses = segments.stream()
            .map(seg -> new SegmentResponse(
                seg.getId(),
                seg.getTitle(),
                seg.getCategoryName(),
                seg.getStartedAt(),
                seg.getEndedAt(),
                seg.getStartOffsetMs(),
                seg.getEndOffsetMs()
            ))
            .toList();

        List<ViewMetricTimelineEntity> timelines = timelineRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        List<TimelineDataPoint> timelineResponses = timelines.stream()
            .map(t -> new TimelineDataPoint(t.getTimestamp().toEpochMilli(), t.getViewerCount()))
            .toList();

        SessionSummaryResponse summaryResponse = sessionRepository.findBySessionId(sessionId)
            .map(session -> new SessionSummaryResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getCategoryName(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getPeakViewers(),
                session.getAverageViewerCount(),
                session.getSubscriberChatRatio()
            ))
            .orElse(null);

        List<AnalysisDataPoint> mergedOneMinutePoints = loadMergedHistoryPoints(streamId, sessionId);
        long bucketIntervalMs = determineBucketIntervalMs(summaryResponse, mergedOneMinutePoints);

        List<AnalysisDataPoint> downsampledPoints = downsampleDataPoints(mergedOneMinutePoints, bucketIntervalMs);
        List<TimelineDataPoint> downsampledTimelines = downsampleTimeline(timelines, bucketIntervalMs);

        return new AnalysisResponse(streamId, downsampledPoints, segmentResponses, downsampledTimelines, summaryResponse);
    }

    private List<AnalysisDataPoint> loadMergedHistoryPoints(String streamId, String sessionId) {
        List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, sessionId);
        List<AnalysisDataPoint> rawDataPoints = aggregateToOneMinuteIntervals(analysisRepository.findRawHistory(streamId, sessionId));

        if (summaryDataPoints.isEmpty()) {
            return rawDataPoints;
        }
        if (rawDataPoints.isEmpty()) {
            return summaryDataPoints;
        }

        Map<Long, AnalysisDataPoint> merged = new TreeMap<>();
        for (AnalysisDataPoint p : summaryDataPoints) {
            merged.put(p.timestamp(), p);
        }
        for (AnalysisDataPoint p : rawDataPoints) {
            merged.putIfAbsent(p.timestamp(), p);
        }
        return new ArrayList<>(merged.values());
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
}
