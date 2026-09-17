package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.model.GrassLevel;
import io.slice.stream.apiserver.streamer.domain.model.GrassTile;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.domain.service.StreakCalculator;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StreamerGrassQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DAYS = 90;
    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 365;
    private static final int MAX_STREAK_SCAN_DAYS = 365;

    private final StreamerDailyStatRepository dailyStatRepository;
    private final JpaStreamSessionRepository sessionRepository;
    private final StreakCalculator streakCalculator;
    private final Clock clock;

    public StreamerGrassQueryService(
        StreamerDailyStatRepository dailyStatRepository,
        JpaStreamSessionRepository sessionRepository,
        StreakCalculator streakCalculator
    ) {
        this(dailyStatRepository, sessionRepository, streakCalculator, Clock.system(KST));
    }

    public StreamerGrassQueryService(
        StreamerDailyStatRepository dailyStatRepository,
        JpaStreamSessionRepository sessionRepository,
        StreakCalculator streakCalculator,
        Clock clock
    ) {
        this.dailyStatRepository = dailyStatRepository;
        this.sessionRepository = sessionRepository;
        this.streakCalculator = streakCalculator;
        this.clock = clock;
    }

    public StreamerGrassResponse getGrassData(String channelId, Integer days) {
        if (days != null && (days < MIN_DAYS || days > MAX_DAYS)) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "잔디 조회 일수는 " + MIN_DAYS + "일 이상 " + MAX_DAYS + "일 이하이어야 합니다: " + days
            );
        }

        int targetDays = (days != null) ? days : DEFAULT_DAYS;
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate startDate = today.minusDays(targetDays - 1L);

        List<StreamerDailyStat> recordedStats = dailyStatRepository.findByChannelIdAndDateRange(
            channelId, startDate, today
        );

        Map<LocalDate, StreamerDailyStat> statMap = recordedStats.stream()
            .collect(Collectors.toMap(StreamerDailyStat::statDate, Function.identity(), (a, b) -> b));

        Instant now = Instant.now(clock);
        Optional<StreamSessionEntity> activeSession = sessionRepository.findActiveSession(channelId);

        List<GrassTile> tiles = new ArrayList<>(targetDays);
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(today)) {
            if (currentDate.equals(today)) {
                tiles.add(buildTodayTile(currentDate, statMap.get(currentDate), activeSession, now));
            } else {
                tiles.add(buildPastTile(currentDate, statMap.get(currentDate)));
            }
            currentDate = currentDate.plusDays(1);
        }

        List<LocalDate> recentActiveDates = dailyStatRepository.findRecentActiveDates(
            channelId, today, MAX_STREAK_SCAN_DAYS
        );
        Set<LocalDate> streakActiveDates = new HashSet<>(recentActiveDates);
        if (activeSession.isPresent()) {
            streakActiveDates.add(today);
        }

        int currentStreak = streakCalculator.calculate(streakActiveDates, today);
        long totalDurationSeconds = tiles.stream().mapToLong(GrassTile::durationSeconds).sum();
        int totalBroadcastDays = (int) tiles.stream().filter(tile -> tile.durationSeconds() > 0).count();

        log.debug("스트리머 잔디 데이터 조회 완료: channelId={}, 조회일수={}, 스트릭={}", channelId, targetDays, currentStreak);

        return new StreamerGrassResponse(
            channelId,
            currentStreak,
            totalDurationSeconds,
            totalBroadcastDays,
            tiles
        );
    }

    private GrassTile buildPastTile(LocalDate date, StreamerDailyStat stat) {
        if (stat != null && stat.broadcastDurationSeconds() > 0) {
            GrassLevel level = GrassLevel.fromDurationSeconds(stat.broadcastDurationSeconds());
            return new GrassTile(
                date,
                level,
                stat.broadcastDurationSeconds(),
                stat.averageViewers(),
                stat.peakViewers(),
                stat.representativeTitle(),
                stat.dominantCategory()
            );
        }
        return GrassTile.empty(date);
    }

    private GrassTile buildTodayTile(
        LocalDate today,
        StreamerDailyStat stat,
        Optional<StreamSessionEntity> activeSession,
        Instant now
    ) {
        long baseDuration = stat != null ? stat.broadcastDurationSeconds() : 0L;
        int peakViewers = stat != null ? stat.peakViewers() : 0;
        int avgViewers = stat != null ? stat.averageViewers() : 0;
        String title = stat != null ? stat.representativeTitle() : null;
        String category = stat != null ? stat.dominantCategory() : null;

        if (activeSession.isPresent()) {
            StreamSessionEntity session = activeSession.get();
            Instant todayStart = today.atStartOfDay(KST).toInstant();
            Instant liveStart = session.getStartedAt().isAfter(todayStart) ? session.getStartedAt() : todayStart;
            long liveSeconds = Math.max(0L, Duration.between(liveStart, now).getSeconds());

            if (session.getPeakViewers() != null && session.getPeakViewers() > peakViewers) {
                peakViewers = session.getPeakViewers();
            }

            int liveAvg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
            long totalTodayDuration = baseDuration + liveSeconds;
            if (totalTodayDuration > 0) {
                double totalWeightedViewerSeconds = ((double) avgViewers * baseDuration) + ((double) liveAvg * liveSeconds);
                avgViewers = (int) Math.round(totalWeightedViewerSeconds / totalTodayDuration);
            }

            baseDuration = totalTodayDuration;

            if (title == null) {
                title = session.getTitle();
            }
            if (category == null) {
                category = session.getCategoryName();
            }
        }

        if (baseDuration > 0) {
            GrassLevel level = GrassLevel.fromDurationSeconds(baseDuration);
            return new GrassTile(today, level, baseDuration, avgViewers, peakViewers, title, category);
        }

        return GrassTile.empty(today);
    }
}
