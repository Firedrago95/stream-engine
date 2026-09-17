package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerCategoryDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerHeaderDto;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse.StreamerKpiSummaryDto;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
@Transactional(readOnly = true)
public class StreamerProfileQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAYS_30 = 30;
    private static final int DAYS_7 = 7;
    private static final int TOP_CATEGORY_LIMIT = 5;

    private final JpaStreamRepository streamRepository;
    private final StreamerDailyStatRepository dailyStatRepository;
    private final JpaStreamSessionRepository sessionRepository;

    public StreamerProfileResponse getProfile(String channelId) {
        StreamEntity stream = streamRepository.findByStreamId(channelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.STREAM_NOT_FOUND, "존재하지 않는 스트리머 채널입니다: " + channelId));

        Instant now = Instant.now();
        LocalDate today = LocalDate.now(KST);
        LocalDate start30d = today.minusDays(DAYS_30 - 1L);
        LocalDate start7d = today.minusDays(DAYS_7 - 1L);

        List<StreamerDailyStat> stats30d = dailyStatRepository.findByChannelIdAndDateRange(channelId, start30d, today);

        int followerGrowth30d = stats30d.stream()
            .mapToInt(stat -> stat.followerGrowth() != null ? stat.followerGrowth() : 0)
            .sum();

        int followerGrowth7d = stats30d.stream()
            .filter(stat -> !stat.statDate().isBefore(start7d))
            .mapToInt(stat -> stat.followerGrowth() != null ? stat.followerGrowth() : 0)
            .sum();

        boolean isLive = stream.isLive() && stream.getLastUpdateAt().isAfter(now.minus(2, ChronoUnit.MINUTES));
        int currentFollowers = stream.getFollowerCount() != null ? stream.getFollowerCount() : 0;

        StreamerHeaderDto header = new StreamerHeaderDto(
            stream.getStreamId(),
            stream.getStreamerName(),
            stream.getProfileImageUrl(),
            isLive,
            currentFollowers,
            followerGrowth7d,
            followerGrowth30d
        );

        Set<LocalDate> broadcastDates = stats30d.stream()
            .filter(stat -> stat.broadcastDurationSeconds() > 0)
            .map(StreamerDailyStat::statDate)
            .collect(Collectors.toSet());

        long totalBroadcastDurationSeconds = stats30d.stream()
            .mapToLong(StreamerDailyStat::broadcastDurationSeconds)
            .sum();

        double totalHoursWatched = stats30d.stream()
            .mapToDouble(StreamerDailyStat::hoursWatched)
            .sum();

        int peakViewers = stats30d.stream()
            .mapToInt(StreamerDailyStat::peakViewers)
            .max()
            .orElse(0);

        if (isLive) {
            broadcastDates.add(today);
            Optional<StreamSessionEntity> activeSession = sessionRepository.findActiveSession(channelId);
            if (activeSession.isPresent()) {
                Instant todayStart = today.atStartOfDay(KST).toInstant();
                Instant sessionStart = activeSession.get().getStartedAt();
                Instant liveStart = sessionStart.isAfter(todayStart) ? sessionStart : todayStart;
                long liveSeconds = Math.max(0L, Duration.between(liveStart, now).getSeconds());
                totalBroadcastDurationSeconds += liveSeconds;
                if (activeSession.get().getPeakViewers() != null && activeSession.get().getPeakViewers() > peakViewers) {
                    peakViewers = activeSession.get().getPeakViewers();
                }
            }
        }

        int broadcastDays30d = broadcastDates.size();
        double attendanceRate30d = Math.round(((double) broadcastDays30d / DAYS_30 * 100.0) * 10.0) / 10.0;

        int averageViewers = 0;
        if (totalBroadcastDurationSeconds > 0) {
            double weightedSum = stats30d.stream()
                .mapToDouble(s -> s.averageViewers() * s.broadcastDurationSeconds())
                .sum();
            averageViewers = (int) Math.round(weightedSum / totalBroadcastDurationSeconds);
        }

        StreamerKpiSummaryDto summary = new StreamerKpiSummaryDto(
            averageViewers,
            peakViewers,
            totalBroadcastDurationSeconds,
            Math.round(totalHoursWatched * 10.0) / 10.0,
            followerGrowth30d,
            broadcastDays30d,
            attendanceRate30d
        );

        List<StreamerCategoryDto> mostPlayedCategories = calculateMostPlayedCategories(channelId, now);

        log.debug("스트리머 프로필 및 요약 통계 조회 완료: channelId={}", channelId);

        return new StreamerProfileResponse(header, summary, mostPlayedCategories);
    }

    private List<StreamerCategoryDto> calculateMostPlayedCategories(String channelId, Instant now) {
        Instant since = now.minus(DAYS_30, ChronoUnit.DAYS);
        List<StreamSessionEntity> sessions = sessionRepository.findSessionsSince(channelId, since);

        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> categoryDurations = new HashMap<>();
        Map<String, Long> categoryViewerWeighted = new HashMap<>();

        long grandTotalDuration = 0L;

        for (StreamSessionEntity session : sessions) {
            String category = (session.getCategoryName() != null && !session.getCategoryName().isBlank())
                ? session.getCategoryName()
                : "기타";

            Instant start = session.getStartedAt();
            Instant end = session.getEndedAt() != null ? session.getEndedAt() : now;
            long duration = Math.max(0L, Duration.between(start, end).getSeconds());

            if (duration == 0) {
                continue;
            }

            int avgViewers = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;

            categoryDurations.merge(category, duration, Long::sum);
            categoryViewerWeighted.merge(category, avgViewers * duration, Long::sum);
            grandTotalDuration += duration;
        }

        if (grandTotalDuration == 0) {
            return Collections.emptyList();
        }

        final long totalSec = grandTotalDuration;

        return categoryDurations.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
            .limit(TOP_CATEGORY_LIMIT)
            .map(entry -> {
                String category = entry.getKey();
                long duration = entry.getValue();
                double percentage = Math.round(((double) duration / totalSec * 100.0) * 10.0) / 10.0;
                int avgViewers = (int) Math.round((double) categoryViewerWeighted.getOrDefault(category, 0L) / duration);
                return new StreamerCategoryDto(category, duration, percentage, avgViewers);
            })
            .toList();
    }
}
