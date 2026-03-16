package io.slice.stream.apiserver.analysis.application.service;

import io.slice.stream.apiserver.analysis.domain.AnalysisRepository;
import io.slice.stream.apiserver.analysis.domain.AnalysisSignal;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse;
import io.slice.stream.apiserver.analysis.presentation.dto.AnalysisResponse.AnalysisDataPoint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    public List<String> getAvailableDates(String streamId, LocalDate before, int limit) {
        LocalDate cursorDate = (before != null) ? before : LocalDate.now(KST).plusYears(2);

        return analysisRepository.findAvailableDates(streamId, cursorDate, limit).stream()
            .map(LocalDate::toString)
            .toList();
    }

    public AnalysisResponse getHistoryAnalysis(String streamId, LocalDate date) {
        Instant startOfDay = date.atTime(6,0).atZone(KST).toInstant();
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        // 과거 요약 데이터를 우선 조회
        List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, startOfDay, endOfDay);

        if(!summaryDataPoints.isEmpty()) {
            return new AnalysisResponse(streamId, summaryDataPoints);
        }

        // 요약 데이터가 없다면, 원본 데이터 조회 후 1분 단위 압축 처리
        List<AnalysisDataPoint> rawDataPoints = analysisRepository.findRawHistory(streamId, startOfDay, endOfDay);
        List<AnalysisDataPoint> aggregatedData =aggregateToOneMinuteIntervals(rawDataPoints);

        return new AnalysisResponse(streamId, aggregatedData);
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
