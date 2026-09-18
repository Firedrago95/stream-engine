package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerCategoryDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerHeaderDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerKpiSummaryDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StreamerProfileQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAYS_30 = 30;
    private static final int DAYS_7 = 7;
    private static final int TOP_CATEGORIES_LIMIT = 5;
    private static final long NOISE_THRESHOLD_SECONDS = 180L;

    private final JpaStreamRepository streamRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final Clock clock;

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository
    ) {
        this(streamRepository, sessionRepository, Clock.system(KST));
    }

    @Autowired
    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        Clock clock
    ) {
        this.streamRepository = streamRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public StreamerProfileResponse getProfile(String channelId) {
        StreamEntity stream = findStreamOrThrow(channelId);

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate start30d = today.minusDays(DAYS_30 - 1L);
        Instant start30dInstant = start30d.atStartOfDay(KST).toInstant();
        Instant start7dInstant = today.minusDays(DAYS_7 - 1L).atStartOfDay(KST).toInstant();

        List<StreamSessionEntity> sessions30d = sessionRepository.findSessionsOverlapping(channelId, start30dInstant, now);

        boolean isLive = isStreamLive(stream, now);
        SessionKpiAccumulator kpi = aggregateSessions(sessions30d, start30dInstant, start7dInstant, start30d, today, now, isLive);

        StreamerHeaderDto header = buildHeader(stream, isLive, kpi.followerGrowth7d(), kpi.followerGrowth30d());
        StreamerKpiSummaryDto summary = buildKpiSummary(kpi);
        List<StreamerCategoryDto> mostPlayedCategories = calculateMostPlayedCategories(sessions30d, start30dInstant, now);

        log.debug("스트리머 프로필 및 요약 통계 조회 완료: channelId={}", channelId);

        return new StreamerProfileResponse(header, summary, mostPlayedCategories);
    }

    private StreamEntity findStreamOrThrow(String channelId) {
        return streamRepository.findByStreamId(channelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_NOT_FOUND, "존재하지 않는 스트리머 채널입니다: " + channelId));
    }

    private boolean isStreamLive(StreamEntity stream, Instant now) {
        return stream.isLive() && stream.getLastUpdateAt().isAfter(now.minus(2, ChronoUnit.MINUTES));
    }

    private SessionKpiAccumulator aggregateSessions(
        List<StreamSessionEntity> sessions,
        Instant start30dInstant,
        Instant start7dInstant,
        LocalDate start30d,
        LocalDate today,
        Instant now,
        boolean isLive
    ) {
        int followerGrowth30d = 0;
        int followerGrowth7d = 0;
        Set<LocalDate> broadcastDates = new HashSet<>();
        long totalDuration = 0L;
        double totalWeightedViewerSeconds = 0.0;
        int peakViewers = 0;

        for (StreamSessionEntity session : sessions) {
            Instant sStart = session.getStartedAt();
            Instant sEnd = session.getEndedAt() != null ? session.getEndedAt() : now;

            Instant effectiveStart = sStart.isBefore(start30dInstant) ? start30dInstant : sStart;
            long dur = Math.max(0L, Duration.between(effectiveStart, sEnd).getSeconds());

            if (session.getEndedAt() != null && dur < NOISE_THRESHOLD_SECONDS) {
                continue;
            }

            totalDuration += dur;

            int sPeak = session.getPeakViewers() != null ? session.getPeakViewers() : 0;
            peakViewers = Math.max(peakViewers, sPeak);

            int sAvg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
            totalWeightedViewerSeconds += (double) dur * sAvg;

            Integer fGrowth = session.getSessionFollowerGrowth();
            if (fGrowth != null) {
                followerGrowth30d += fGrowth;
                if (!sStart.isBefore(start7dInstant)) {
                    followerGrowth7d += fGrowth;
                }
            }

            accumulateBroadcastDates(broadcastDates, effectiveStart, sEnd, session.getEndedAt() != null, start30d, today);
        }

        if (isLive) {
            broadcastDates.add(today);
        }

        return new SessionKpiAccumulator(
            totalDuration,
            totalWeightedViewerSeconds,
            peakViewers,
            followerGrowth30d,
            followerGrowth7d,
            broadcastDates
        );
    }

    private void accumulateBroadcastDates(
        Set<LocalDate> broadcastDates,
        Instant effectiveStart,
        Instant sEnd,
        boolean isFinished,
        LocalDate start30d,
        LocalDate today
    ) {
        Instant exclusiveEnd = (isFinished && sEnd.isAfter(effectiveStart))
            ? sEnd.minusNanos(1)
            : sEnd;

        LocalDate dStart = effectiveStart.atZone(KST).toLocalDate();
        LocalDate dEnd = exclusiveEnd.atZone(KST).toLocalDate();
        LocalDate cur = dStart.isBefore(start30d) ? start30d : dStart;
        LocalDate limit = dEnd.isAfter(today) ? today : dEnd;

        while (!cur.isAfter(limit)) {
            broadcastDates.add(cur);
            cur = cur.plusDays(1L);
        }
    }

    private StreamerHeaderDto buildHeader(StreamEntity stream, boolean isLive, int growth7d, int growth30d) {
        int currentFollowers = stream.getFollowerCount() != null ? stream.getFollowerCount() : 0;
        return new StreamerHeaderDto(
            stream.getStreamId(),
            stream.getStreamerName(),
            stream.getProfileImageUrl(),
            isLive,
            currentFollowers,
            growth7d,
            growth30d
        );
    }

    private StreamerKpiSummaryDto buildKpiSummary(SessionKpiAccumulator kpi) {
        int broadcastDays30d = kpi.broadcastDates().size();
        double attendanceRate30d = Math.round(((double) broadcastDays30d / DAYS_30 * 100.0) * 10.0) / 10.0;

        int averageViewers = 0;
        if (kpi.totalBroadcastDurationSeconds() > 0) {
            averageViewers = (int) Math.round(kpi.totalWeightedViewerSeconds() / kpi.totalBroadcastDurationSeconds());
        }
        double totalHoursWatched = Math.round((kpi.totalWeightedViewerSeconds() / 3600.0) * 10.0) / 10.0;

        return new StreamerKpiSummaryDto(
            averageViewers,
            kpi.peakViewers(),
            kpi.totalBroadcastDurationSeconds(),
            totalHoursWatched,
            kpi.followerGrowth30d(),
            broadcastDays30d,
            attendanceRate30d
        );
    }

    private List<StreamerCategoryDto> calculateMostPlayedCategories(
        List<StreamSessionEntity> sessions,
        Instant rangeStart,
        Instant now
    ) {
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> durationByCategory = new HashMap<>();
        Map<String, Double> weightedViewerSecondsByCategory = new HashMap<>();

        for (StreamSessionEntity session : sessions) {
            String category = session.getCategoryName();
            if (category == null || category.isBlank()) {
                continue;
            }

            Instant start = session.getStartedAt();
            Instant effectiveStart = start.isBefore(rangeStart) ? rangeStart : start;
            Instant end = session.getEndedAt() != null ? session.getEndedAt() : now;
            long durationSeconds = Math.max(0L, Duration.between(effectiveStart, end).getSeconds());

            if (session.getEndedAt() != null && durationSeconds < NOISE_THRESHOLD_SECONDS) {
                continue;
            }

            if (durationSeconds <= 0) {
                continue;
            }

            durationByCategory.merge(category, durationSeconds, Long::sum);

            int avgViewers = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
            double viewerSeconds = (double) avgViewers * durationSeconds;
            weightedViewerSecondsByCategory.merge(category, viewerSeconds, Double::sum);
        }

        long totalDurationAll = durationByCategory.values().stream().mapToLong(Long::longValue).sum();
        if (totalDurationAll <= 0) {
            return Collections.emptyList();
        }

        return durationByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(TOP_CATEGORIES_LIMIT)
            .map(entry -> {
                String category = entry.getKey();
                long durationSec = entry.getValue();
                double shareRatio = Math.round(((double) durationSec / totalDurationAll * 100.0) * 10.0) / 10.0;
                double viewerSec = weightedViewerSecondsByCategory.getOrDefault(category, 0.0);
                int avgViewers = (int) Math.round(viewerSec / durationSec);

                return new StreamerCategoryDto(category, durationSec, shareRatio, avgViewers);
            })
            .toList();
    }

    private record SessionKpiAccumulator(
        long totalBroadcastDurationSeconds,
        double totalWeightedViewerSeconds,
        int peakViewers,
        int followerGrowth30d,
        int followerGrowth7d,
        Set<LocalDate> broadcastDates
    ) {}
}
