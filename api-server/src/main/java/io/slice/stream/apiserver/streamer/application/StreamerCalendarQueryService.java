package io.slice.stream.apiserver.streamer.application;

import io.slice.stream.apiserver.global.error.BusinessException;
import io.slice.stream.apiserver.global.error.ErrorCode;
import io.slice.stream.apiserver.stream.infrastructure.JpaStreamSessionRepository;
import io.slice.stream.apiserver.stream.infrastructure.entity.StreamSessionEntity;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse;
import io.slice.stream.apiserver.streamer.presentation.dto.StreamerCalendarResponse.CalendarSessionDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class StreamerCalendarQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;
    private static final int MIN_MONTH = 1;
    private static final int MAX_MONTH = 12;
    private static final long NOISE_THRESHOLD_SECONDS = 180L;

    private final JpaStreamSessionRepository sessionRepository;
    private final Clock clock;

    public StreamerCalendarQueryService(JpaStreamSessionRepository sessionRepository) {
        this(sessionRepository, Clock.system(KST));
    }

    @Autowired
    public StreamerCalendarQueryService(
        JpaStreamSessionRepository sessionRepository,
        Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public StreamerCalendarResponse getMonthlyCalendar(String channelId, int year, int month) {
        validateYearMonth(year, month);

        Instant rangeStart = calculateRangeStart(year, month);
        Instant rangeEnd = calculateRangeEnd(year, month);

        List<StreamSessionEntity> sessions = sessionRepository.findSessionsOverlapping(
            channelId, rangeStart, rangeEnd
        );

        Instant now = Instant.now(clock);

        List<CalendarSessionDto> dtos = sessions.stream()
            .map(session -> toCalendarSessionDto(session, now))
            .filter(dto -> !isNoiseSession(dto))
            .toList();

        log.debug("스트리머 월간 달력 세션 조회 완료: channelId={}, year={}, month={}, totalSessions={}, validSessions={}",
            channelId, year, month, sessions.size(), dtos.size());

        return new StreamerCalendarResponse(channelId, year, month, dtos);
    }

    private void validateYearMonth(int year, int month) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "조회 년도는 " + MIN_YEAR + "년 이상 " + MAX_YEAR + "년 이하이어야 합니다: " + year
            );
        }
        if (month < MIN_MONTH || month > MAX_MONTH) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE,
                "조회 월은 " + MIN_MONTH + "월 이상 " + MAX_MONTH + "월 이하이어야 합니다: " + month
            );
        }
    }

    private Instant calculateRangeStart(int year, int month) {
        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        return firstDayOfMonth.atStartOfDay(KST).toInstant();
    }

    private Instant calculateRangeEnd(int year, int month) {
        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        return firstDayOfMonth.plusMonths(1L).atStartOfDay(KST).toInstant();
    }

    private CalendarSessionDto toCalendarSessionDto(StreamSessionEntity session, Instant now) {
        boolean isLive = (session.getEndedAt() == null);
        Instant effectiveEndedAt = isLive ? now : session.getEndedAt();
        long durationSeconds = Math.max(0L, Duration.between(session.getStartedAt(), effectiveEndedAt).getSeconds());

        int peak = session.getPeakViewers() != null ? session.getPeakViewers() : 0;
        int avg = session.getAverageViewerCount() != null ? session.getAverageViewerCount() : 0;
        String title = session.getTitle() != null ? session.getTitle() : "";
        String category = session.getCategoryName() != null ? session.getCategoryName() : "";

        return new CalendarSessionDto(
            session.getSessionId(),
            title,
            category,
            session.getStartedAt(),
            session.getEndedAt(),
            durationSeconds,
            peak,
            avg,
            isLive
        );
    }

    private boolean isNoiseSession(CalendarSessionDto dto) {
        return !dto.isLive() && dto.durationSeconds() < NOISE_THRESHOLD_SECONDS;
    }
}
