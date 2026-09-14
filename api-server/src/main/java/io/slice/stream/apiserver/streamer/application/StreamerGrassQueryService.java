package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.streamer.domain.model.GrassLevel;
import io.slice.stream.apiserver.streamer.domain.model.GrassTile;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.domain.service.StreakCalculator;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreamerGrassQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DAYS = 90;

    private final StreamerDailyStatRepository dailyStatRepository;
    private final StreakCalculator streakCalculator;

    public StreamerGrassResponse getGrassData(String channelId, Integer days) {
        int targetDays = (days != null && days > 0) ? days : DEFAULT_DAYS;
        LocalDate today = LocalDate.now(KST);
        LocalDate startDate = today.minusDays(targetDays - 1L);

        List<StreamerDailyStat> recordedStats = dailyStatRepository.findByChannelIdAndDateRange(
            channelId, startDate, today
        );

        Map<LocalDate, StreamerDailyStat> statMap = recordedStats.stream()
            .collect(Collectors.toMap(StreamerDailyStat::statDate, Function.identity(), (a, b) -> b));

        List<GrassTile> tiles = new ArrayList<>(targetDays);
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(today)) {
            StreamerDailyStat stat = statMap.get(currentDate);
            if (stat != null && stat.broadcastDurationSeconds() > 0) {
                GrassLevel level = GrassLevel.fromDurationSeconds(stat.broadcastDurationSeconds());
                tiles.add(new GrassTile(
                    currentDate,
                    level,
                    stat.broadcastDurationSeconds(),
                    stat.averageViewers(),
                    stat.peakViewers(),
                    stat.representativeTitle(),
                    stat.dominantCategory()
                ));
            } else {
                tiles.add(GrassTile.empty(currentDate));
            }
            currentDate = currentDate.plusDays(1);
        }

        Set<LocalDate> activeDates = statMap.entrySet().stream()
            .filter(entry -> entry.getValue().broadcastDurationSeconds() > 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        int currentStreak = streakCalculator.calculate(activeDates, today);
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
}
