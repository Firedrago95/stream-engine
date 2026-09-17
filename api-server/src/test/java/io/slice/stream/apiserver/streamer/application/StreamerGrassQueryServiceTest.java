package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.model.GrassLevel;
import io.slice.stream.apiserver.streamer.domain.model.GrassTile;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.domain.service.StreakCalculator;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerGrassQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_NOW = Instant.parse("2026-09-17T06:00:00Z"); // 15:00 KST
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, KST);

    @Mock
    private StreamerDailyStatRepository dailyStatRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Mock
    private StreakCalculator streakCalculator;

    private StreamerGrassQueryService grassQueryService;

    @BeforeEach
    void setUp() {
        grassQueryService = new StreamerGrassQueryService(dailyStatRepository, sessionRepository, streakCalculator, fixedClock);
    }

    @Test
    void 지정된_기간만큼_빠짐없이_타일_목록을_생성하여_반환한다() {
        String channelId = "ch_test_1";
        int days = 7;
        LocalDate today = LocalDate.now(fixedClock.withZone(KST));
        LocalDate startDate = today.minusDays(days - 1L);

        StreamerDailyStat statToday = StreamerDailyStat.of(
            channelId, today, 18000L, 4500, 9000, 22.5, 10000, 50, "오늘 방송", "Talk", 1
        );

        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), eq(startDate), eq(today)))
            .willReturn(List.of(statToday));
        given(dailyStatRepository.findRecentActiveDates(eq(channelId), eq(today), any(Integer.class)))
            .willReturn(List.of(today));
        given(streakCalculator.calculate(any(), eq(today)))
            .willReturn(1);

        StreamerGrassResponse response = grassQueryService.getGrassData(channelId, days);

        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.currentStreak()).isEqualTo(1);
        assertThat(response.totalBroadcastDays()).isEqualTo(1);
        assertThat(response.totalBroadcastDurationSeconds()).isEqualTo(18000L);
        assertThat(response.tiles()).hasSize(7);

        GrassTile todayTile = response.tiles().get(response.tiles().size() - 1);
        assertThat(todayTile.date()).isEqualTo(today);
        assertThat(todayTile.level()).isEqualTo(GrassLevel.LEVEL_3);
        assertThat(todayTile.representativeTitle()).isEqualTo("오늘 방송");
        assertThat(todayTile.dominantCategory()).isEqualTo("Talk");

        GrassTile emptyTile = response.tiles().get(0);
        assertThat(emptyTile.level()).isEqualTo(GrassLevel.LEVEL_0);
        assertThat(emptyTile.durationSeconds()).isZero();
    }

    @Test
    void 조회_일수가_null이면_기본_90일_잔디를_조회한다() {
        String channelId = "ch_test_2";
        LocalDate today = LocalDate.now(fixedClock.withZone(KST));
        LocalDate startDate = today.minusDays(89L);

        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), eq(startDate), eq(today)))
            .willReturn(List.of());
        given(dailyStatRepository.findRecentActiveDates(eq(channelId), eq(today), any(Integer.class)))
            .willReturn(List.of());
        given(streakCalculator.calculate(any(), eq(today)))
            .willReturn(0);

        StreamerGrassResponse response = grassQueryService.getGrassData(channelId, null);

        assertThat(response.tiles()).hasSize(90);
        assertThat(response.currentStreak()).isZero();
        assertThat(response.totalBroadcastDays()).isZero();
    }

    @Test
    void 조회_일수가_7일_미만이거나_365일_초과이면_INVALID_INPUT_VALUE_예외가_발생한다() {
        String channelId = "ch_test_3";

        assertThatThrownBy(() -> grassQueryService.getGrassData(channelId, 6))
            .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> grassQueryService.getGrassData(channelId, 366))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void 조회_기간보다_긴_연속_스트릭도_온전한_길이로_반환된다() {
        String channelId = "ch_test_4";
        int days = 7;
        LocalDate today = LocalDate.now(fixedClock.withZone(KST));
        LocalDate startDate = today.minusDays(days - 1L);

        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), eq(startDate), eq(today)))
            .willReturn(List.of());
        given(dailyStatRepository.findRecentActiveDates(eq(channelId), eq(today), any(Integer.class)))
            .willReturn(List.of(today));
        given(streakCalculator.calculate(any(), eq(today)))
            .willReturn(120);

        StreamerGrassResponse response = grassQueryService.getGrassData(channelId, days);

        assertThat(response.tiles()).hasSize(7);
        assertThat(response.currentStreak()).isEqualTo(120);
    }

    @Test
    void 방송_진행_중인_활성_세션이_있으면_오늘_잔디_타일에_실시간_방송_시간이_합산되어_점등된다() {
        String channelId = "ch_live";
        int days = 7;
        LocalDate today = LocalDate.now(fixedClock.withZone(KST));
        LocalDate startDate = today.minusDays(days - 1L);

        StreamSessionEntity activeSession = new StreamSessionEntity(
            channelId, "live_1", "실시간 방송 중", "Just Chatting", FIXED_NOW.minusSeconds(7200)
        );
        activeSession.updatePeakViewers(5000);
        activeSession.updateAverageViewerCount(3000);

        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), eq(startDate), eq(today)))
            .willReturn(List.of());
        given(sessionRepository.findActiveSession(eq(channelId)))
            .willReturn(Optional.of(activeSession));
        given(dailyStatRepository.findRecentActiveDates(eq(channelId), eq(today), any(Integer.class)))
            .willReturn(List.of());
        given(streakCalculator.calculate(any(), eq(today)))
            .willReturn(1);

        StreamerGrassResponse response = grassQueryService.getGrassData(channelId, days);

        GrassTile todayTile = response.tiles().get(response.tiles().size() - 1);
        assertThat(todayTile.date()).isEqualTo(today);
        assertThat(todayTile.durationSeconds()).isEqualTo(7200);
        assertThat(todayTile.level()).isNotEqualTo(GrassLevel.LEVEL_0);
        assertThat(todayTile.peakViewers()).isEqualTo(5000);
        assertThat(todayTile.avgViewers()).isEqualTo(3000);
        assertThat(response.currentStreak()).isEqualTo(1);
    }

    @Test
    void 오늘_기종료된_세션과_현재_라이브_세션이_함께_있으면_방송_시간에_비례한_가중_평균_시청자가_계산된다() {
        String channelId = "ch_multi";
        int days = 7;
        LocalDate today = LocalDate.now(fixedClock.withZone(KST));
        LocalDate startDate = today.minusDays(days - 1L);

        // 오늘 1부 방송: 3600초(1시간), 평균 1000명, 최고 1500명
        StreamerDailyStat morningStat = StreamerDailyStat.of(
            channelId, today, 3600L, 1000, 1500, 1.0, 10000, 10, "1부 방송", "Talk", 1
        );

        // 오늘 2부 방송(라이브 중): 3600초(1시간), 평균 3000명, 최고 4000명
        StreamSessionEntity activeSession = new StreamSessionEntity(
            channelId, "live_2", "2부 실시간", "Game", FIXED_NOW.minusSeconds(3600)
        );
        activeSession.updatePeakViewers(4000);
        activeSession.updateAverageViewerCount(3000);

        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), eq(startDate), eq(today)))
            .willReturn(List.of(morningStat));
        given(sessionRepository.findActiveSession(eq(channelId)))
            .willReturn(Optional.of(activeSession));
        given(dailyStatRepository.findRecentActiveDates(eq(channelId), eq(today), any(Integer.class)))
            .willReturn(List.of(today));
        given(streakCalculator.calculate(any(), eq(today)))
            .willReturn(1);

        StreamerGrassResponse response = grassQueryService.getGrassData(channelId, days);

        GrassTile todayTile = response.tiles().get(response.tiles().size() - 1);
        assertThat(todayTile.date()).isEqualTo(today);
        assertThat(todayTile.durationSeconds()).isEqualTo(7200); // 3600 + 3600
        // 가중 평균: (1000 * 3600 + 3000 * 3600) / 7200 = 2000명
        assertThat(todayTile.avgViewers()).isEqualTo(2000);
        assertThat(todayTile.peakViewers()).isEqualTo(4000); // max(1500, 4000)
    }
}
