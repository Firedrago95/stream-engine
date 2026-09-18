package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse.CalendarSessionDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerCalendarQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_NOW = Instant.parse("2026-09-18T10:00:00Z");

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    private StreamerCalendarQueryService calendarQueryService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, KST);
        calendarQueryService = new StreamerCalendarQueryService(sessionRepository, fixedClock);
    }

    @Test
    void 정상_년월_조회시_KST_월초부터_익월초까지_범위로_세션을_조회한다() {
        String channelId = "ch-normal";
        int year = 2026;
        int month = 9;

        Instant rangeStart = Instant.parse("2026-08-31T15:00:00Z"); // 2026-09-01 00:00:00 KST
        Instant rangeEnd = Instant.parse("2026-09-30T15:00:00Z");   // 2026-10-01 00:00:00 KST

        StreamSessionEntity session = new StreamSessionEntity(
            channelId, "sess-1", "정규 방송", "리그 오브 레전드",
            Instant.parse("2026-09-10T11:23:00Z")
        );
        session.finishSession(Instant.parse("2026-09-10T21:59:00Z"), 5000, 3000.0);

        when(sessionRepository.findSessionsOverlapping(eq(channelId), eq(rangeStart), eq(rangeEnd)))
            .thenReturn(List.of(session));

        StreamerCalendarResponse response = calendarQueryService.getMonthlyCalendar(channelId, year, month);

        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.month()).isEqualTo(9);
        assertThat(response.sessions()).hasSize(1);

        CalendarSessionDto item = response.sessions().get(0);
        assertThat(item.sessionId()).isEqualTo("sess-1");
        assertThat(item.title()).isEqualTo("정규 방송");
        assertThat(item.categoryName()).isEqualTo("리그 오브 레전드");
        assertThat(item.durationSeconds()).isEqualTo(38160L);
        assertThat(item.peakViewers()).isEqualTo(5000);
        assertThat(item.averageViewers()).isEqualTo(3000);
        assertThat(item.isLive()).isFalse();
    }

    @Test
    void 삼분_미만의_종료된_세션은_노이즈로_판단하여_결과에서_제외한다() {
        String channelId = "ch-noise";
        int year = 2026;
        int month = 9;

        Instant rangeStart = Instant.parse("2026-08-31T15:00:00Z");
        Instant rangeEnd = Instant.parse("2026-09-30T15:00:00Z");

        // 170초 (2분 50초) 방송 후 종료된 노이즈 세션
        StreamSessionEntity noiseSession = new StreamSessionEntity(
            channelId, "noise-sess", "잠깐 킴", "Just Chatting",
            Instant.parse("2026-09-11T05:00:00Z")
        );
        noiseSession.finishSession(Instant.parse("2026-09-11T05:02:50Z"), 100, 50.0);

        // 180초 (3분 정각) 정상 세션
        StreamSessionEntity validSession = new StreamSessionEntity(
            channelId, "valid-sess", "3분 방송", "Just Chatting",
            Instant.parse("2026-09-11T06:00:00Z")
        );
        validSession.finishSession(Instant.parse("2026-09-11T06:03:00Z"), 200, 100.0);

        when(sessionRepository.findSessionsOverlapping(eq(channelId), eq(rangeStart), eq(rangeEnd)))
            .thenReturn(List.of(noiseSession, validSession));

        StreamerCalendarResponse response = calendarQueryService.getMonthlyCalendar(channelId, year, month);

        assertThat(response.sessions()).hasSize(1);
        assertThat(response.sessions().get(0).sessionId()).isEqualTo("valid-sess");
    }

    @Test
    void 삼분_미만이어도_진행중인_라이브_세션은_제외하지_않고_포함한다() {
        String channelId = "ch-live";
        int year = 2026;
        int month = 9;

        Instant rangeStart = Instant.parse("2026-08-31T15:00:00Z");
        Instant rangeEnd = Instant.parse("2026-09-30T15:00:00Z");

        // 방금 1분 전에 켜서 아직 진행 중인 라이브 세션 (endedAt == null)
        StreamSessionEntity liveSession = new StreamSessionEntity(
            channelId, "live-sess", "방금 킨 방송", "파이어 엠블렘",
            FIXED_NOW.minusSeconds(60)
        );

        when(sessionRepository.findSessionsOverlapping(eq(channelId), eq(rangeStart), eq(rangeEnd)))
            .thenReturn(List.of(liveSession));

        StreamerCalendarResponse response = calendarQueryService.getMonthlyCalendar(channelId, year, month);

        assertThat(response.sessions()).hasSize(1);
        CalendarSessionDto item = response.sessions().get(0);
        assertThat(item.sessionId()).isEqualTo("live-sess");
        assertThat(item.isLive()).isTrue();
        assertThat(item.durationSeconds()).isEqualTo(60L);
    }

    @Test
    void 유효하지_않은_월을_입력하면_INVALID_INPUT_VALUE_예외가_발생한다() {
        String channelId = "ch-val";

        assertThatThrownBy(() -> calendarQueryService.getMonthlyCalendar(channelId, 2026, 0))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> calendarQueryService.getMonthlyCalendar(channelId, 2026, 13))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 유효하지_않은_년도를_입력하면_INVALID_INPUT_VALUE_예외가_발생한다() {
        String channelId = "ch-val";

        assertThatThrownBy(() -> calendarQueryService.getMonthlyCalendar(channelId, 1999, 9))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> calendarQueryService.getMonthlyCalendar(channelId, 2101, 9))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }
}
