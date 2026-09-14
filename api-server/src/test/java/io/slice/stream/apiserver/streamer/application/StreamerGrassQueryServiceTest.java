package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.streamer.domain.model.GrassLevel;
import io.slice.stream.apiserver.streamer.domain.model.GrassTile;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.domain.service.StreakCalculator;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerGrassResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    @Mock
    private StreamerDailyStatRepository dailyStatRepository;

    @Mock
    private StreakCalculator streakCalculator;

    private StreamerGrassQueryService grassQueryService;

    @BeforeEach
    void setUp() {
        grassQueryService = new StreamerGrassQueryService(dailyStatRepository, streakCalculator);
    }

    @Test
    void 지정된_기간만큼_빠짐없이_타일_목록을_생성하여_반환한다() {
        String channelId = "ch_test_1";
        int days = 7;
        LocalDate today = LocalDate.now(KST);
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
        LocalDate today = LocalDate.now(KST);
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
        LocalDate today = LocalDate.now(KST);
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
}
