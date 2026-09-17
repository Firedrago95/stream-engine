package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.model.DailySplitSegment;
import io.slice.stream.apiserver.streamer.domain.service.SessionMidnightSplitter;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerDailyStatEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamerDailyStatCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JpaStreamerDailyStatRepository dailyStatRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final SessionMidnightSplitter sessionMidnightSplitter;

    @Transactional
    public void recordSession(StreamSessionEntity session) {
        if (session == null || session.getStartedAt() == null || session.getEndedAt() == null) {
            return;
        }

        List<DailySplitSegment> segments = sessionMidnightSplitter.split(
            session.getStartedAt(),
            session.getEndedAt(),
            session.getPeakViewers() != null ? session.getPeakViewers() : 0,
            session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0,
            session.getTitle(),
            session.getCategoryName()
        );

        Set<LocalDate> affectedDates = segments.stream()
            .map(DailySplitSegment::date)
            .collect(Collectors.toSet());

        for (LocalDate statDate : affectedDates) {
            recalculateAndSaveDailyStat(session.getStreamId(), statDate);
        }
    }

    @Transactional
    public void recordActiveSessionsDailySnapshot(LocalDate targetDate) {
        if (targetDate == null) {
            return;
        }

        Instant dayEnd = targetDate.plusDays(1).atStartOfDay(KST).toInstant();
        List<StreamSessionEntity> activeSessions = sessionRepository.findActiveSessionsStartedBefore(dayEnd);

        log.info("[DailyStats] 새벽 정기 일별 통계 스냅샷 정산 대상 활성 세션: {}건", activeSessions.size());

        for (StreamSessionEntity activeSession : activeSessions) {
            recalculateAndSaveDailyStat(activeSession.getStreamId(), targetDate);
        }
    }

    private void recalculateAndSaveDailyStat(String channelId, LocalDate statDate) {
        Instant dayStart = statDate.atStartOfDay(KST).toInstant();
        Instant dayEnd = statDate.plusDays(1).atStartOfDay(KST).toInstant();

        List<StreamSessionEntity> overlappingSessions = sessionRepository.findSessionsOverlapping(
            channelId, dayStart, dayEnd
        );

        if (overlappingSessions.isEmpty()) {
            return;
        }

        DailyStatCalculation metrics = calculateAggregatedMetrics(overlappingSessions, statDate);
        if (metrics.durationSeconds() <= 0) {
            return;
        }

        saveOrUpdateDailyStat(channelId, statDate, metrics);
    }

    private DailyStatCalculation calculateAggregatedMetrics(List<StreamSessionEntity> sessions, LocalDate statDate) {
        long totalDurationSeconds = 0;
        long totalWeightedViewerSeconds = 0;
        int peakViewers = 0;
        int segmentCount = 0;
        String representativeTitle = null;
        String dominantCategory = null;
        int maxSegmentPeak = -1;

        for (StreamSessionEntity session : sessions) {
            Instant effectiveEndedAt = session.getEndedAt() != null ? session.getEndedAt() : Instant.now();
            int sessionPeak = session.getPeakViewers() != null ? session.getPeakViewers() : 0;
            int sessionAvg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;

            List<DailySplitSegment> segments = sessionMidnightSplitter.split(
                session.getStartedAt(),
                effectiveEndedAt,
                sessionPeak,
                sessionAvg,
                session.getTitle(),
                session.getCategoryName()
            );

            for (DailySplitSegment segment : segments) {
                if (!segment.date().equals(statDate)) {
                    continue;
                }

                totalDurationSeconds += segment.durationSeconds();
                totalWeightedViewerSeconds += (segment.durationSeconds() * segment.avgViewers());
                peakViewers = Math.max(peakViewers, segment.peakViewers());
                segmentCount++;

                if (segment.peakViewers() > maxSegmentPeak || representativeTitle == null) {
                    maxSegmentPeak = segment.peakViewers();
                    representativeTitle = segment.title();
                    dominantCategory = segment.category();
                }
            }
        }

        int weightedAvgViewers = totalDurationSeconds > 0
            ? (int) Math.round((double) totalWeightedViewerSeconds / totalDurationSeconds)
            : 0;
        double hoursWatched = (double) (totalDurationSeconds * weightedAvgViewers) / 3600.0;

        return new DailyStatCalculation(
            totalDurationSeconds,
            weightedAvgViewers,
            peakViewers,
            hoursWatched,
            representativeTitle,
            dominantCategory,
            segmentCount
        );
    }

    private void saveOrUpdateDailyStat(String channelId, LocalDate statDate, DailyStatCalculation metrics) {
        Optional<StreamerDailyStatEntity> existingOpt = dailyStatRepository.findByChannelIdAndStatDate(channelId, statDate);

        if (existingOpt.isPresent()) {
            StreamerDailyStatEntity existing = existingOpt.get();
            existing.updateMetrics(
                metrics.durationSeconds(),
                metrics.avgViewers(),
                metrics.peakViewers(),
                metrics.hoursWatched(),
                metrics.title(),
                metrics.category(),
                metrics.sessionCount()
            );
        } else {
            StreamerDailyStatEntity newEntity = new StreamerDailyStatEntity(
                null,
                channelId,
                statDate,
                metrics.durationSeconds(),
                metrics.avgViewers(),
                metrics.peakViewers(),
                metrics.hoursWatched(),
                null,
                null,
                metrics.title(),
                metrics.category(),
                metrics.sessionCount()
            );
            dailyStatRepository.save(newEntity);
        }
    }

    private record DailyStatCalculation(
        long durationSeconds,
        int avgViewers,
        int peakViewers,
        double hoursWatched,
        String title,
        String category,
        int sessionCount
    ) {
    }
}
