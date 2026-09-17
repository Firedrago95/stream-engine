package io.slice.stream.apiserver.streamer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.domain.service.SessionMidnightSplitter;
import io.slice.stream.apiserver.streamer.infrastructure.JpaStreamerDailyStatRepository;
import io.slice.stream.apiserver.streamer.infrastructure.entity.StreamerDailyStatEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class StreamerDailyStatCommandServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private JpaStreamerDailyStatRepository dailyStatRepository;

    @Mock
    private JpaStreamSessionRepository sessionRepository;

    @Captor
    private ArgumentCaptor<StreamerDailyStatEntity> entityCaptor;

    private SessionMidnightSplitter splitter;
    private StreamerDailyStatCommandService service;

    @BeforeEach
    void setUp() {
        splitter = new SessionMidnightSplitter(KST);
        service = new StreamerDailyStatCommandService(dailyStatRepository, sessionRepository, splitter);
    }

    @Test
    void 당일_시작되고_종료된_단일_세션은_당일_일별_통계로_정상_적재된다() {
        Instant startedAt = Instant.parse("2026-09-13T05:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T09:00:00Z");
        StreamSessionEntity session = new StreamSessionEntity("ch-1", "sess-1", "당일 방송", "Talk", startedAt);
        session.finishSession(endedAt, 5000, 3000);

        Instant dayStart = LocalDate.of(2026, 9, 13).atStartOfDay(KST).toInstant();
        Instant dayEnd = LocalDate.of(2026, 9, 14).atStartOfDay(KST).toInstant();

        given(sessionRepository.findSessionsOverlapping("ch-1", dayStart, dayEnd))
            .willReturn(List.of(session));
        given(dailyStatRepository.findByChannelIdAndStatDate("ch-1", LocalDate.of(2026, 9, 13)))
            .willReturn(Optional.empty());

        service.recordSession(session);

        verify(dailyStatRepository).save(entityCaptor.capture());
        StreamerDailyStatEntity saved = entityCaptor.getValue();
        assertThat(saved.getChannelId()).isEqualTo("ch-1");
        assertThat(saved.getStatDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(saved.getBroadcastDurationSeconds()).isEqualTo(14400L);
        assertThat(saved.getAverageViewers()).isEqualTo(3000);
        assertThat(saved.getPeakViewers()).isEqualTo(5000);
        assertThat(saved.getHoursWatched()).isEqualTo(12000.0);
        assertThat(saved.getSessionCount()).isEqualTo(1);
    }

    @Test
    void 자정을_넘겨_익일_새벽에_종료된_세션은_두_날짜의_일별_통계로_분할_적재된다() {
        Instant startedAt = Instant.parse("2026-09-13T11:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T17:00:00Z");
        StreamSessionEntity session = new StreamSessionEntity("ch-1", "sess-1", "야간 방송", "Game", startedAt);
        session.finishSession(endedAt, 8000, 4000);

        LocalDate day1 = LocalDate.of(2026, 9, 13);
        LocalDate day2 = LocalDate.of(2026, 9, 14);

        Instant day1Start = day1.atStartOfDay(KST).toInstant();
        Instant day1End = day1.plusDays(1).atStartOfDay(KST).toInstant();
        Instant day2Start = day2.atStartOfDay(KST).toInstant();
        Instant day2End = day2.plusDays(1).atStartOfDay(KST).toInstant();

        given(sessionRepository.findSessionsOverlapping("ch-1", day1Start, day1End))
            .willReturn(List.of(session));
        given(sessionRepository.findSessionsOverlapping("ch-1", day2Start, day2End))
            .willReturn(List.of(session));

        given(dailyStatRepository.findByChannelIdAndStatDate("ch-1", day1))
            .willReturn(Optional.empty());
        given(dailyStatRepository.findByChannelIdAndStatDate("ch-1", day2))
            .willReturn(Optional.empty());

        service.recordSession(session);

        verify(dailyStatRepository, atLeastOnce()).save(entityCaptor.capture());
        List<StreamerDailyStatEntity> savedList = entityCaptor.getAllValues();
        assertThat(savedList).hasSize(2);

        StreamerDailyStatEntity savedDay1 = savedList.stream()
            .filter(e -> e.getStatDate().equals(day1))
            .findFirst()
            .orElseThrow();
        assertThat(savedDay1.getBroadcastDurationSeconds()).isEqualTo(14400L);
        assertThat(savedDay1.getAverageViewers()).isEqualTo(4000);

        StreamerDailyStatEntity savedDay2 = savedList.stream()
            .filter(e -> e.getStatDate().equals(day2))
            .findFirst()
            .orElseThrow();
        assertThat(savedDay2.getBroadcastDurationSeconds()).isEqualTo(7200L);
        assertThat(savedDay2.getAverageViewers()).isEqualTo(4000);
    }

    @Test
    void 새벽_배치에서_진행중인_세션의_어제_구간이_스냅샷으로_선반영된다() {
        LocalDate yesterday = LocalDate.of(2026, 9, 13);
        Instant yesterdayEnd = yesterday.plusDays(1).atStartOfDay(KST).toInstant();

        Instant startedAt = Instant.parse("2026-09-13T11:00:00Z");
        StreamSessionEntity activeSession = new StreamSessionEntity("ch-active", "sess-act", "심야 마라톤", "Game", startedAt);
        activeSession.updateAverageViewerCount(6000);

        given(sessionRepository.findActiveSessionsStartedBefore(yesterdayEnd))
            .willReturn(List.of(activeSession));

        Instant dayStart = yesterday.atStartOfDay(KST).toInstant();
        given(sessionRepository.findSessionsOverlapping("ch-active", dayStart, yesterdayEnd))
            .willReturn(List.of(activeSession));
        given(dailyStatRepository.findByChannelIdAndStatDate("ch-active", yesterday))
            .willReturn(Optional.empty());

        service.recordActiveSessionsDailySnapshot(yesterday);

        verify(dailyStatRepository).save(entityCaptor.capture());
        StreamerDailyStatEntity saved = entityCaptor.getValue();
        assertThat(saved.getStatDate()).isEqualTo(yesterday);
        assertThat(saved.getBroadcastDurationSeconds()).isEqualTo(14400L);
        assertThat(saved.getAverageViewers()).isEqualTo(6000);
    }

    @Test
    void 새벽_배치_스냅샷_이후_실제_방종되어도_시간이_이중_누적되지_않고_멱등하게_최종값으로_갱신된다() {
        LocalDate yesterday = LocalDate.of(2026, 9, 13);
        LocalDate today = LocalDate.of(2026, 9, 14);

        Instant startedAt = Instant.parse("2026-09-13T11:00:00Z");
        Instant endedAt = Instant.parse("2026-09-13T23:00:00Z");

        StreamSessionEntity finishedSession = new StreamSessionEntity("ch-active", "sess-act", "심야 마라톤", "Game", startedAt);
        finishedSession.finishSession(endedAt, 10000, 6500);

        StreamerDailyStatEntity existingYesterdaySnapshot = new StreamerDailyStatEntity(
            1L, "ch-active", yesterday, 14400L, 6000, 8000, 24000.0,
            null, null, "심야 마라톤", "Game", 1
        );

        Instant day1Start = yesterday.atStartOfDay(KST).toInstant();
        Instant day1End = yesterday.plusDays(1).atStartOfDay(KST).toInstant();
        Instant day2Start = today.atStartOfDay(KST).toInstant();
        Instant day2End = today.plusDays(1).atStartOfDay(KST).toInstant();

        given(sessionRepository.findSessionsOverlapping("ch-active", day1Start, day1End))
            .willReturn(List.of(finishedSession));
        given(sessionRepository.findSessionsOverlapping("ch-active", day2Start, day2End))
            .willReturn(List.of(finishedSession));

        given(dailyStatRepository.findByChannelIdAndStatDate("ch-active", yesterday))
            .willReturn(Optional.of(existingYesterdaySnapshot));
        given(dailyStatRepository.findByChannelIdAndStatDate("ch-active", today))
            .willReturn(Optional.empty());

        service.recordSession(finishedSession);

        assertThat(existingYesterdaySnapshot.getBroadcastDurationSeconds()).isEqualTo(14400L);
        assertThat(existingYesterdaySnapshot.getAverageViewers()).isEqualTo(6500);
        assertThat(existingYesterdaySnapshot.getPeakViewers()).isEqualTo(10000);
    }


    @Test
    void 하루에_여러_세션이_진행되어도_시간과_가중평균이_정확히_누적된다() {
        LocalDate today = LocalDate.of(2026, 9, 13);
        Instant session1Start = Instant.parse("2026-09-13T01:00:00Z"); // 10:00 KST
        Instant session1End = Instant.parse("2026-09-13T03:00:00Z");   // 12:00 KST (2시간 = 7200초, 평균 2000, 피크 3000)
        StreamSessionEntity sess1 = new StreamSessionEntity("ch-multi", "s1", "낮방송", "Talk", session1Start);
        sess1.finishSession(session1End, 3000, 2000);

        Instant session2Start = Instant.parse("2026-09-13T09:00:00Z"); // 18:00 KST
        Instant session2End = Instant.parse("2026-09-13T13:00:00Z");   // 22:00 KST (4시간 = 14400초, 평균 5000, 피크 7000)
        StreamSessionEntity sess2 = new StreamSessionEntity("ch-multi", "s2", "밤방송", "Game", session2Start);
        sess2.finishSession(session2End, 7000, 5000);

        Instant dayStart = today.atStartOfDay(KST).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(KST).toInstant();

        given(sessionRepository.findSessionsOverlapping("ch-multi", dayStart, dayEnd))
            .willReturn(List.of(sess1, sess2));
        given(dailyStatRepository.findByChannelIdAndStatDate("ch-multi", today))
            .willReturn(Optional.empty());

        service.recordSession(sess2);

        verify(dailyStatRepository).save(entityCaptor.capture());
        StreamerDailyStatEntity saved = entityCaptor.getValue();
        assertThat(saved.getBroadcastDurationSeconds()).isEqualTo(21600L); // 6시간
        // 가중 평균: (7200 * 2000 + 14400 * 5000) / 21600 = (14400000 + 72000000) / 21600 = 86400000 / 21600 = 4000
        assertThat(saved.getAverageViewers()).isEqualTo(4000);
        assertThat(saved.getPeakViewers()).isEqualTo(7000);
        assertThat(saved.getSessionCount()).isEqualTo(2);
        assertThat(saved.getRepresentativeTitle()).isEqualTo("밤방송");
        assertThat(saved.getDominantCategory()).isEqualTo("Game");
    }
}

