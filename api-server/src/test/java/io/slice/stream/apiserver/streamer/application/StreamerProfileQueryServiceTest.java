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
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerProfileResponse;
import java.time.Clock;
import java.time.Instant;
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
    private static final Instant FIXED_NOW = Instant.parse("2026-09-17T06:00:00Z"); // 15:00 KST
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, KST);

    @Mock
    private JpaStreamRepository streamRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    private StreamerProfileQueryService profileQueryService;

    @BeforeEach
    void setUp() {
        profileQueryService = new StreamerProfileQueryService(
            streamRepository,
            sessionRepository,
            fixedClock
        );
    }

    @Test
    void 프로필_헤더와_30일_KPI_요약_및_주력_카테고리_Top5가_정상_계산되어_반환된다() {
        String channelId = "ch_tester";
        Instant now = FIXED_NOW;

        StreamEntity stream = new StreamEntity(channelId, "테스트스트리머");
        stream.heartbeat("테스트스트리머", "오늘 방송", "https://image.png", "Talk", 2500);
        stream.updateChannelMetrics(50000, 1200);
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(30, ChronoUnit.SECONDS));

        // 세션 1: 10,000초 방송, 평균 2000명, 피크 5000명, 팔로워 증가 100명
        StreamSessionEntity session1 = new StreamSessionEntity(
            channelId, "sess_1", "오늘 방송", "Talk", now.minusSeconds(10000)
        );
        session1.finishSession(now, 5000, 2000.0);
        session1.updateSessionFollowerGrowth(100);

        // 세션 2 (5일 전): 20,000초 방송, 평균 3000명, 피크 6000명, 팔로워 증가 50명
        Instant pastStart = now.minus(5, ChronoUnit.DAYS);
        StreamSessionEntity session2 = new StreamSessionEntity(
            channelId, "sess_2", "과거 방송", "League of Legends", pastStart
        );
        session2.finishSession(pastStart.plusSeconds(20000), 6000, 3000.0);
        session2.updateSessionFollowerGrowth(50);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(sessionRepository.findSessionsOverlapping(eq(channelId), any(), any()))
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
        assertThat(response.summary().hoursWatched()).isEqualTo(22222.2);
        assertThat(response.summary().broadcastDays30d()).isEqualTo(2);
        assertThat(response.summary().attendanceRate30d()).isEqualTo(6.7);

        assertThat(response.mostPlayedCategories()).hasSize(2);
        assertThat(response.mostPlayedCategories().get(0).categoryName()).isEqualTo("League of Legends");
        assertThat(response.mostPlayedCategories().get(0).percentage()).isEqualTo(66.7);
        assertThat(response.mostPlayedCategories().get(0).averageViewers()).isEqualTo(3000);

        assertThat(response.mostPlayedCategories().get(1).categoryName()).isEqualTo("Talk");
        assertThat(response.mostPlayedCategories().get(1).percentage()).isEqualTo(33.3);
    }

    @Test
    void 현재_라이브_방송_중인_세션이_있으면_해당_세션의_시청자수와_시간도_가중_평균에_합산된다() {
        String channelId = "ch_live_calc";
        Instant now = FIXED_NOW;

        StreamEntity stream = new StreamEntity(channelId, "라이브스트리머");
        stream.heartbeat("라이브스트리머", "실시간 방송", "https://image.png", "Just Chatting", 4000);
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(10, ChronoUnit.SECONDS));

        // 과거 세션 (어제): 10,000초 동안 평균 2,000명 (가중합 20,000,000)
        Instant pastStart = now.minus(1, ChronoUnit.DAYS);
        StreamSessionEntity pastSession = new StreamSessionEntity(
            channelId, "past_session_1", "어제 방송", "Game", pastStart
        );
        pastSession.finishSession(pastStart.plusSeconds(10000), 3000, 2000.0);

        // 현재 라이브 세션 (진행 중): FIXED_NOW 기준 10,000초 전 시작, 평균 4,000명 (가중합 40,000,000)
        StreamSessionEntity activeSession = new StreamSessionEntity(
            channelId, "live_session_1", "실시간 방송", "Just Chatting", now.minusSeconds(10000)
        );
        activeSession.updatePeakViewers(5000);
        activeSession.updateAverageViewerCount(4000);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(sessionRepository.findSessionsOverlapping(eq(channelId), any(), any()))
            .willReturn(List.of(pastSession, activeSession));

        StreamerProfileResponse response = profileQueryService.getProfile(channelId);

        // 총 방송 시간 = 10,000(과거) + 10,000(라이브) = 20,000초
        assertThat(response.summary().totalBroadcastDurationSeconds()).isEqualTo(20000L);
        // 가중 평균 = (2000 * 10000 + 4000 * 10000) / 20000 = 3000명
        assertThat(response.summary().averageViewers()).isEqualTo(3000);
        assertThat(response.summary().peakViewers()).isEqualTo(5000);
        assertThat(response.summary().broadcastDays30d()).isEqualTo(2);
    }

    @Test
    void 자정에_정확히_종료된_세션은_익일이_아닌_전일까지만_방송일수로_반영된다() {
        String channelId = "ch_midnight_test";
        Instant now = FIXED_NOW;

        StreamEntity stream = new StreamEntity(channelId, "자정테스트");
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(1, ChronoUnit.HOURS));

        // 9월 16일 20:00 KST 시작 ~ 9월 17일 00:00:00 KST 정확히 자정 종료 (4시간)
        Instant sStart = Instant.parse("2026-09-16T11:00:00Z"); // 20:00 KST
        Instant sEnd = Instant.parse("2026-09-16T15:00:00Z");   // 00:00:00 KST (9/17)
        StreamSessionEntity session = new StreamSessionEntity(
            channelId, "sess_midnight", "자정 종료 방송", "Chat", sStart
        );
        session.finishSession(sEnd, 1000, 500.0);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(sessionRepository.findSessionsOverlapping(eq(channelId), any(), any()))
            .willReturn(List.of(session));

        StreamerProfileResponse response = profileQueryService.getProfile(channelId);

        // 9월 17일 00:00:00 종료이므로 9월 16일 하루만 방송일수로 인정되어야 함 (2일이 아님)
        assertThat(response.summary().broadcastDays30d()).isEqualTo(1);
        assertThat(response.summary().totalBroadcastDurationSeconds()).isEqualTo(14400L);
    }

    @Test
    void 삼십일_시작_경계에_걸친_밤샘_세션도_정상_조회되어_30일_내_시간만_합산된다() {
        String channelId = "ch_boundary_test";
        Instant now = FIXED_NOW; // 2026-09-17T06:00:00Z

        StreamEntity stream = new StreamEntity(channelId, "경계테스트");
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(1, ChronoUnit.HOURS));

        // 30일 시작 경계: 2026-08-19 00:00:00 KST = 2026-08-18T15:00:00Z
        // 시작은 경계 1시간 전(14:00Z = 23:00 KST), 종료는 경계 3시간 후(18:00Z = 03:00 KST) (총 4시간 중 3시간만 30일 범위)
        Instant sStart = Instant.parse("2026-08-18T14:00:00Z");
        Instant sEnd = Instant.parse("2026-08-18T18:00:00Z");
        StreamSessionEntity session = new StreamSessionEntity(
            channelId, "sess_boundary", "밤샘 방송", "LoL", sStart
        );
        session.finishSession(sEnd, 2000, 1000.0);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(sessionRepository.findSessionsOverlapping(eq(channelId), any(), any()))
            .willReturn(List.of(session));

        StreamerProfileResponse response = profileQueryService.getProfile(channelId);

        // 30일 범위 내 시간인 3시간(10,800초)만 합산되어야 함
        assertThat(response.summary().totalBroadcastDurationSeconds()).isEqualTo(10800L);
        assertThat(response.summary().broadcastDays30d()).isEqualTo(1);
    }

    @Test
    void 삼분_미만의_종료된_단시간_노이즈_세션은_방송일수_및_지표_집계에서_제외된다() {
        String channelId = "ch_noise_test";
        Instant now = FIXED_NOW;

        StreamEntity stream = new StreamEntity(channelId, "노이즈테스트");
        ReflectionTestUtils.setField(stream, "lastUpdateAt", now.minus(1, ChronoUnit.HOURS));

        // 유효 세션: 어제 10,000초 방송
        Instant validStart = now.minus(1, ChronoUnit.DAYS);
        StreamSessionEntity validSession = new StreamSessionEntity(
            channelId, "sess_valid", "정상 방송", "Game", validStart
        );
        validSession.finishSession(validStart.plusSeconds(10000), 1000, 500.0);

        // 노이즈 세션: 3일 전 120초(2분) 방송 후 종료
        Instant noiseStart = now.minus(3, ChronoUnit.DAYS);
        StreamSessionEntity noiseSession = new StreamSessionEntity(
            channelId, "sess_noise", "테스트 방송", "NoiseCategory", noiseStart
        );
        noiseSession.finishSession(noiseStart.plusSeconds(120), 2000, 1500.0);

        given(streamRepository.findByStreamId(channelId)).willReturn(Optional.of(stream));
        given(sessionRepository.findSessionsOverlapping(eq(channelId), any(), any()))
            .willReturn(List.of(validSession, noiseSession));

        StreamerProfileResponse response = profileQueryService.getProfile(channelId);

        // 노이즈 세션은 제외되므로 방송일수는 1일이어야 함 (2일 아님)
        assertThat(response.summary().broadcastDays30d()).isEqualTo(1);
        // 총 방송 시간도 유효 세션의 10,000초만 집계되어야 함
        assertThat(response.summary().totalBroadcastDurationSeconds()).isEqualTo(10000L);
        // 피크 시청자도 노이즈 세션의 2,000명이 아닌 1,000명이어야 함
        assertThat(response.summary().peakViewers()).isEqualTo(1000);
        // 카테고리도 NoiseCategory는 제외되고 Game만 나와야 함
        assertThat(response.mostPlayedCategories()).hasSize(1);
        assertThat(response.mostPlayedCategories().get(0).categoryName()).isEqualTo("Game");
    }

    @Test
    void 존재하지_않는_스트리머인_경우_예외가_발생한다() {
        given(streamRepository.findByStreamId("non_existing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileQueryService.getProfile("non_existing"))
            .isInstanceOf(BusinessException.class);
    }
}
