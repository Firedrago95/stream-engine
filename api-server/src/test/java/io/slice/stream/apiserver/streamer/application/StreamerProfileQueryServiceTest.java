package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamRepository;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamEntity;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.model.StreamerDailyStat;
import io.slice.stream.apiserver.streamer.domain.repository.StreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerProfileQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private StreamerDailyStatRepository dailyStatRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    private StreamerProfileQueryService profileQueryService;

    @BeforeEach
    void setUp() {
        profileQueryService = new StreamerProfileQueryService(
            streamRepository,
            dailyStatRepository,
            sessionRepository
        );
    }

    @Test
    void 프로필_헤더와_30일_KPI_요약_및_주력_카테고리_Top5가_정상_계산되어_반환된다() {
        String channelId = "ch_tester";
        LocalDate today = LocalDate.now(KST);
        Instant now = Instant.now();

        StreamEntity stream = new StreamEntity(channelId, "테스트스트리머");
        stream.heartbeat("테스트스트리머", "오늘 방송", "https://image.png", "Talk", 2500);
        stream.updateChannelMetrics(50000, 1200);
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(30, ChronoUnit.SECONDS));

        StreamerDailyStat stat1 = StreamerDailyStat.of(
            channelId, today, 10000L, 2000, 5000, 5.5, 50000, 100, "오늘 방송", "Talk", 1
        );
        StreamerDailyStat stat2 = StreamerDailyStat.of(
            channelId, today.minusDays(5), 20000L, 3000, 6000, 16.6, 49900, 50, "과거 방송", "LOL", 1
        );

        StreamSessionEntity session1 = new StreamSessionEntity(
            channelId, "sess_1", "토크 방송", "Talk", now.minus(3, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(session1, "endedAt", now.minus(1, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(session1, "averageViewerCount", 2500);

        StreamSessionEntity session2 = new StreamSessionEntity(
            channelId, "sess_2", "롤 방송", "League of Legends", now.minus(6, ChronoUnit.HOURS)
        );
        ReflectionTestUtils.setField(session2, "endedAt", now.minus(3, ChronoUnit.HOURS));
        ReflectionTestUtils.setField(session2, "averageViewerCount", 4000);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(dailyStatRepository.findByChannelIdAndDateRange(eq(channelId), any(), eq(today)))
            .willReturn(List.of(stat1, stat2));
        given(sessionRepository.findSessionsSince(eq(channelId), any()))
            .willReturn(List.of(session1, session2));

        StreamerProfileResponse response = profileQueryService.getProfile(channelId);

        assertThat(response.header().channelId()).isEqualTo(channelId);
        assertThat(response.header().streamerName()).isEqualTo("테스트스트리머");
        assertThat(response.header().isLive()).isTrue();
        assertThat(response.header().currentFollowers()).isEqualTo(50000);
        assertThat(response.header().followerGrowth30d()).isEqualTo(150);
        assertThat(response.header().followerGrowth7d()).isEqualTo(150);

        assertThat(response.summary().peakViewers()).isEqualTo(6000);
        assertThat(response.summary().totalBroadcastDurationSeconds()).isEqualTo(30000L);
        assertThat(response.summary().averageViewers()).isEqualTo(2667);
        assertThat(response.summary().hoursWatched()).isEqualTo(22.1);
        assertThat(response.summary().broadcastDays30d()).isEqualTo(2);
        assertThat(response.summary().attendanceRate30d()).isEqualTo(6.7);

        assertThat(response.mostPlayedCategories()).hasSize(2);
        assertThat(response.mostPlayedCategories().get(0).categoryName()).isEqualTo("League of Legends");
        assertThat(response.mostPlayedCategories().get(0).percentage()).isEqualTo(60.0);
        assertThat(response.mostPlayedCategories().get(0).averageViewers()).isEqualTo(4000);

        assertThat(response.mostPlayedCategories().get(1).categoryName()).isEqualTo("Talk");
        assertThat(response.mostPlayedCategories().get(1).percentage()).isEqualTo(40.0);
    }

    @Test
    void 존재하지_않는_스트리머인_경우_예외가_발생한다() {
        given(streamRepository.findByStreamId("non_existing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileQueryService.getProfile("non_existing"))
            .isInstanceOf(BusinessException.class);
    }
}
