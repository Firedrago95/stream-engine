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
        LocalDate cursorDate = (before != null) ? before : LocalDate.MAX;

        return analysisRepository.findAvailableDates(streamId, cursorDate, limit).stream()
            .map(LocalDate::toString)
            .toList();
    }

    public AnalysisResponse getHistoryAnalysis(String streamId, LocalDate date) {
        Instant startOfDay = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);

        LocalDate boundaryDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(3);

        if (!date.isBefore(boundaryDate)) {
            List<AnalysisDataPoint> rawDataPoints = analysisRepository.findRawHistory(streamId, startOfDay, endOfDay);
            return new AnalysisResponse(streamId, rawDataPoints);
        } else {
            List<AnalysisDataPoint> summaryDataPoints = analysisRepository.findSummaryHistory(streamId, startOfDay, endOfDay);
            return new AnalysisResponse(streamId, summaryDataPoints);
        }
    }
}
