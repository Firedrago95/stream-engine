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

        List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, sessionId);
        List<AnalysisDataPoint> rawDataPoints = aggregateToOneMinuteIntervals(analysisRepository.findRawHistory(streamId, sessionId));

        List<AnalysisDataPoint> points;
        if (summaryDataPoints.isEmpty()) {
            points = rawDataPoints;
        } else if (rawDataPoints.isEmpty()) {
            points = summaryDataPoints;
        } else {
            Map<Long, AnalysisDataPoint> merged = new TreeMap<>();
            for (AnalysisDataPoint p : summaryDataPoints) {
                merged.put(p.timestamp(), p);
            }
            for (AnalysisDataPoint p : rawDataPoints) {
                merged.putIfAbsent(p.timestamp(), p);
            }
            points = new ArrayList<>(merged.values());
        }

        return new AnalysisResponse(streamId, points, segmentResponses, timelineResponses, summaryResponse);
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
