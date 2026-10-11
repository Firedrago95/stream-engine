package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.config.SessionProperties;
import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerFollowerSnapshotRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerFollowerSnapshotEntity;
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

    private final JpaStreamRepository streamRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final StreamerFollowerSnapshotRepository snapshotRepository;
    private final StreamerLeaderboardQueryService leaderboardQueryService;
    private final SessionProperties sessionProperties;
    private final Clock clock;

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository
    ) {
        this(streamRepository, sessionRepository, null, null, new SessionProperties(null, null, 50), Clock.system(KST));
    }

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        Clock clock
    ) {
        this(streamRepository, sessionRepository, null, null, new SessionProperties(null, null, 50), clock);
    }

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        StreamerFollowerSnapshotRepository snapshotRepository,
        Clock clock
    ) {
        this(streamRepository, sessionRepository, snapshotRepository, null, new SessionProperties(null, null, 50), clock);
    }

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        StreamerLeaderboardQueryService leaderboardQueryService,
        Clock clock
    ) {
        this(streamRepository, sessionRepository, null, leaderboardQueryService, new SessionProperties(null, null, 50), clock);
    }

    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        StreamerFollowerSnapshotRepository snapshotRepository,
        StreamerLeaderboardQueryService leaderboardQueryService,
        Clock clock
    ) {
        this(streamRepository, sessionRepository, snapshotRepository, leaderboardQueryService, new SessionProperties(null, null, 50), clock);
    }

    @Autowired
    public StreamerProfileQueryService(
        JpaStreamRepository streamRepository,
        JpaStreamSessionRepository sessionRepository,
        StreamerFollowerSnapshotRepository snapshotRepository,
        StreamerLeaderboardQueryService leaderboardQueryService,
        SessionProperties sessionProperties,
        Clock clock
    ) {
        this.streamRepository = streamRepository;
        this.sessionRepository = sessionRepository;
        this.snapshotRepository = snapshotRepository;
        this.leaderboardQueryService = leaderboardQueryService;
        this.sessionProperties = sessionProperties;
        this.clock = clock;
    }

    public StreamerProfileResponse getProfile(String channelId) {
        StreamEntity stream = findStreamOrThrow(channelId);

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate start30d = today.minusDays(DAYS_30 - 1L);
        Instant start30dInstant = start30d.atStartOfDay(KST).toInstant();

        List<StreamSessionEntity> sessions30d = sessionRepository.findSessionsOverlapping(channelId, start30dInstant, now);

        boolean isLive = isStreamLive(stream, now);
        SessionKpiAccumulator kpi = aggregateSessions(sessions30d, start30dInstant, start30d, today, now, isLive);

        Optional<Integer> cachedAverageViewers = leaderboardQueryService != null
            ? leaderboardQueryService.getCachedAverageViewers(channelId)
            : Optional.empty();

        FollowerGrowthDto followerGrowth = calculateFollowerGrowth(channelId, stream, today);

        StreamerHeaderDto header = buildHeader(stream, isLive, followerGrowth.growth7d(), followerGrowth.growth30d());
        StreamerKpiSummaryDto summary = buildKpiSummary(kpi, followerGrowth.growth30d(), cachedAverageViewers);
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
        LocalDate start30d,
        LocalDate today,
        Instant now,
        boolean isLive
    ) {
        Set<LocalDate> broadcastDates = new HashSet<>();
        long totalDuration = 0L;
        long totalWeightedDurationSeconds = 0L;
        double totalWeightedViewerSeconds = 0.0;
        int peakViewers = 0;

        for (StreamSessionEntity session : sessions) {
            Instant sStart = session.getStartedAt();
            Instant sEnd = session.getEndedAt() != null ? session.getEndedAt() : now;

            Instant effectiveStart = sStart.isBefore(start30dInstant) ? start30dInstant : sStart;
            long dur = Math.max(0L, Duration.between(effectiveStart, sEnd).getSeconds());

            if (session.getEndedAt() != null && dur < sessionProperties.noiseThreshold().getSeconds()) {
                continue;
            }

            totalDuration += dur;

            int sPeak = session.getPeakViewers() != null ? session.getPeakViewers() : 0;
            peakViewers = Math.max(peakViewers, sPeak);

            int sAvg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
            if (sAvg > 0) {
                totalWeightedViewerSeconds += (double) dur * sAvg;
                totalWeightedDurationSeconds += dur;
            }

            accumulateBroadcastDates(broadcastDates, effectiveStart, sEnd, session.getEndedAt() != null, start30d, today);
        }

        if (isLive) {
            broadcastDates.add(today);
        }

        return new SessionKpiAccumulator(
            totalDuration,
            totalWeightedDurationSeconds,
            totalWeightedViewerSeconds,
            peakViewers,
            broadcastDates
        );
    }

    private FollowerGrowthDto calculateFollowerGrowth(String channelId, StreamEntity stream, LocalDate today) {
        if (snapshotRepository == null) {
            return new FollowerGrowthDto(0, 0);
        }

        LocalDate baseline30dDate = today.minusDays(DAYS_30);
        LocalDate start30d = today.minusDays(DAYS_30 - 1L);
        LocalDate baseline7dDate = today.minusDays(DAYS_7);
        LocalDate start7d = today.minusDays(DAYS_7 - 1L);

        List<StreamerFollowerSnapshotEntity> snapshots =
            snapshotRepository.findAllByStreamIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(channelId, baseline30dDate);

        if (snapshots.isEmpty()) {
            return new FollowerGrowthDto(0, 0);
        }

        int currentFollowers = stream.getFollowerCount() != null
            ? stream.getFollowerCount()
            : snapshots.get(snapshots.size() - 1).getFollowerCount();

        int baseline30dFollowers = resolveBaselineFollowers(snapshots, baseline30dDate, start30d, currentFollowers);
        int growth30d = currentFollowers - baseline30dFollowers;

        int baseline7dFollowers = resolveBaselineFollowers(snapshots, baseline7dDate, start7d, currentFollowers);
        int growth7d = currentFollowers - baseline7dFollowers;

        return new FollowerGrowthDto(growth7d, growth30d);
    }

    private int resolveBaselineFollowers(
        List<StreamerFollowerSnapshotEntity> snapshots,
        LocalDate baselineDate,
        LocalDate periodStartDate,
        int fallbackFollowers
    ) {
        for (StreamerFollowerSnapshotEntity snapshot : snapshots) {
            if (snapshot.getSnapshotDate().equals(baselineDate)) {
                return snapshot.getFollowerCount();
            }
        }

        for (StreamerFollowerSnapshotEntity snapshot : snapshots) {
            if (!snapshot.getSnapshotDate().isBefore(periodStartDate)) {
                return snapshot.getFollowerCount();
            }
        }

        return fallbackFollowers;
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

    private StreamerKpiSummaryDto buildKpiSummary(
        SessionKpiAccumulator kpi,
        int followerGrowth30d,
        Optional<Integer> cachedAverageViewers
    ) {
        int broadcastDays30d = kpi.broadcastDates().size();
        double attendanceRate30d = Math.round(((double) broadcastDays30d / DAYS_30 * 100.0) * 10.0) / 10.0;

        int averageViewers = cachedAverageViewers.orElseGet(() -> {
            if (kpi.totalWeightedDurationSeconds() > 0) {
                return (int) Math.round(kpi.totalWeightedViewerSeconds() / kpi.totalWeightedDurationSeconds());
            }
            return 0;
        });
        double totalHoursWatched = Math.round((kpi.totalWeightedViewerSeconds() / 3600.0) * 10.0) / 10.0;

        return new StreamerKpiSummaryDto(
            averageViewers,
            kpi.peakViewers(),
            kpi.totalBroadcastDurationSeconds(),
            totalHoursWatched,
            followerGrowth30d,
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
        Map<String, Long> weightedDurationByCategory = new HashMap<>();
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

            if (session.getEndedAt() != null && durationSeconds < sessionProperties.noiseThreshold().getSeconds()) {
                continue;
            }

            if (durationSeconds <= 0) {
                continue;
            }

            durationByCategory.merge(category, durationSeconds, Long::sum);

            int avgViewers = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
            if (avgViewers > 0) {
                double viewerSeconds = (double) avgViewers * durationSeconds;
                weightedViewerSecondsByCategory.merge(category, viewerSeconds, Double::sum);
                weightedDurationByCategory.merge(category, durationSeconds, Long::sum);
            }
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
                long validDuration = weightedDurationByCategory.getOrDefault(category, 0L);
                int avgViewers = validDuration > 0 ? (int) Math.round(viewerSec / validDuration) : 0;

                return new StreamerCategoryDto(category, durationSec, shareRatio, avgViewers);
            })
            .toList();
    }

    private record SessionKpiAccumulator(
        long totalBroadcastDurationSeconds,
        long totalWeightedDurationSeconds,
        double totalWeightedViewerSeconds,
        int peakViewers,
        Set<LocalDate> broadcastDates
    ) {}

    private record FollowerGrowthDto(int growth7d, int growth30d) {}
}
