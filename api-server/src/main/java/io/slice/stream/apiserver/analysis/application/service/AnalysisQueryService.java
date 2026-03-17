package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import io.slice.stream.apiserver.analysis.presentation.dto.SessionResponse;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public AnalysisResponse getRecentAnalysis(String streamId) {
        List<AnalysisSignal> signals = analysisRepository.findRecentSignals(streamId, FIND_LIMIT);

        List<AnalysisDataPoint> dataPoints = signals.stream()
            .map(s -> new AnalysisDataPoint(
                s.timestamp().toEpochMilli(),
                s.firepower(),
                s.status()
            ))
            .toList();

        return new AnalysisResponse(streamId, dataPoints);
    }

    public List<SessionResponse> getAvailableSessions(String streamId, int limit) {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("M월 d일 HH:mm 방송").withZone(KST);
        return sessionRepository.findRecentSessionsByStreamId(streamId, org.springframework.data.domain.PageRequest.of(0, limit))
            .stream()
            .map(session -> new SessionResponse(session.getSessionId(), formatter.format(session.getStartedAt())))
            .toList();
    }

    public AnalysisResponse getHistoryAnalysis(String streamId, String sessionId) {
        List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, sessionId);
        if(!summaryDataPoints.isEmpty()) return new AnalysisResponse(streamId, summaryDataPoints);

        List<AnalysisDataPoint> rawDataPoints = analysisRepository.findRawHistory(streamId, sessionId);
        return new AnalysisResponse(streamId, aggregateToOneMinuteIntervals(rawDataPoints));
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
