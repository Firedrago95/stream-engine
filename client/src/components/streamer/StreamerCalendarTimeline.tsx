import React, { useState, useEffect, useMemo, useRef } from 'react';

export interface CalendarSessionDto {
  sessionId: string;
  title: string;
  categoryName: string;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number;
  peakViewers: number;
  averageViewers: number;
  isLive: boolean;
}

interface StreamerCalendarTimelineProps {
  channelId: string;
}

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

interface SessionPalette {
  name: string;
  gradient: string;
  borderColor: string;
  glow: string;
  textColor: string;
  badgeBg: string;
}

const DEFAULT_PALETTE: SessionPalette = {
  name: '에메랄드',
  gradient: 'from-[#143226] to-[#0c1f18]',
  borderColor: 'border-[#00FFA3]/40',
  glow: 'rgba(0, 255, 163, 0.08)',
  textColor: 'text-[#00FFA3]',
  badgeBg: 'bg-[#00FFA3]',
};

const LIVE_PALETTE: SessionPalette = {
  name: '라이브',
  gradient: 'from-[#7f1d1d] to-[#450a0a]',
  borderColor: 'border-[#ef4444]',
  glow: 'rgba(239, 68, 68, 0.4)',
  textColor: 'text-white',
  badgeBg: 'bg-[#ef4444]',
};

function getKstDate(date: Date): { year: number; month: number; date: number; day: number; hours: number; minutes: number } {
  const utc = date.getTime() + date.getTimezoneOffset() * 60000;
  const kst = new Date(utc + 9 * 3600000);
  return {
    year: kst.getFullYear(),
    month: kst.getMonth() + 1,
    date: kst.getDate(),
    day: kst.getDay(),
    hours: kst.getHours(),
    minutes: kst.getMinutes(),
  };
}

function parseKstInstant(isoString: string): Date {
  return new Date(isoString);
}

function formatDurationHours(durationSeconds: number): string {
  if (durationSeconds <= 0) return '0시간';
  const hours = durationSeconds / 3600;
  if (hours < 1) {
    const mins = Math.max(1, Math.round(durationSeconds / 60));
    return `${mins}분`;
  }
  return `${hours.toFixed(1)}시간`;
}

function formatKstTimeOnly(date: Date): string {
  const kst = getKstDate(date);
  const hh = String(kst.hours).padStart(2, '0');
  const mm = String(kst.minutes).padStart(2, '0');
  return `${hh}:${mm}`;
}

function formatKstDateTime(date: Date): string {
  const kst = getKstDate(date);
  const mm = String(kst.month).padStart(2, '0');
  const dd = String(kst.date).padStart(2, '0');
  const hh = String(kst.hours).padStart(2, '0');
  const min = String(kst.minutes).padStart(2, '0');
  return `${mm}.${dd} ${hh}:${min}`;
}

function formatSessionTimeRange(startDate: Date, endDate: Date | null): string {
  const startStr = formatKstDateTime(startDate);
  if (!endDate) return `${startStr} ~ 진행 중`;
  const kstStart = getKstDate(startDate);
  const kstEnd = getKstDate(endDate);
  const isSameDay = kstStart.year === kstEnd.year && kstStart.month === kstEnd.month && kstStart.date === kstEnd.date;
  const endStr = isSameDay ? formatKstTimeOnly(endDate) : formatKstDateTime(endDate);
  return `${startStr} ~ ${endStr}`;
}

interface CalendarDay {
  dateKey: string;
  year: number;
  month: number;
  day: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  dayOfWeek: number;
}

interface WeekSegment {
  session: CalendarSessionDto;
  palette: SessionPalette;
  leftPercent: number;
  widthPercent: number;
  isStartOfSession: boolean;
  isEndOfSession: boolean;
  laneIndex: number;
  barLabel: string;
}

export const StreamerCalendarTimeline: React.FC<StreamerCalendarTimelineProps> = ({ channelId }) => {
  const todayKst = useMemo(() => getKstDate(new Date()), []);
  const [selectedYear, setSelectedYear] = useState<number>(todayKst.year);
  const [selectedMonth, setSelectedMonth] = useState<number>(todayKst.month);

  const [sessions, setSessions] = useState<CalendarSessionDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [hoveredSession, setHoveredSession] = useState<{
    session: CalendarSessionDto;
    palette: SessionPalette;
    x: number;
    y: number;
  } | null>(null);

  const [popoverDate, setPopoverDate] = useState<{
    dateKey: string;
    dayNum: number;
    sessions: CalendarSessionDto[];
    x: number;
    y: number;
  } | null>(null);

  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!channelId) return;

    setLoading(true);
    setError(null);

    const controller = new AbortController();
    fetch(`${API_BASE_URL}/api/v1/streamers/${channelId}/calendar?year=${selectedYear}&month=${selectedMonth}`, {
      signal: controller.signal,
    })
      .then((res) => {
        if (!res.ok) throw new Error('월간 달력 데이터를 불러오지 못했습니다.');
        return res.json();
      })
      .then((data) => {
        setSessions(data.sessions || []);
        setLoading(false);
      })
      .catch((err) => {
        if (err.name !== 'AbortError') {
          console.error('캘린더 세션 조회 실패:', err);
          setError(err.message);
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [channelId, selectedYear, selectedMonth]);

  const handlePrevMonth = () => {
    if (selectedMonth === 1) {
      setSelectedYear((y) => y - 1);
      setSelectedMonth(12);
    } else {
      setSelectedMonth((m) => m - 1);
    }
  };

  const handleNextMonth = () => {
    if (selectedMonth === 12) {
      setSelectedYear((y) => y + 1);
      setSelectedMonth(1);
    } else {
      setSelectedMonth((m) => m + 1);
    }
  };

  const handleGoToday = () => {
    setSelectedYear(todayKst.year);
    setSelectedMonth(todayKst.month);
  };

  const calendarWeeks = useMemo(() => {
    const firstDay = new Date(Date.UTC(selectedYear, selectedMonth - 1, 1, 0, 0, 0));
    const firstDayKst = getKstDate(firstDay);
    const startDayOfWeek = firstDayKst.day;

    const daysInMonth = new Date(Date.UTC(selectedYear, selectedMonth, 0, 0, 0, 0)).getUTCDate();
    const daysInPrevMonth = new Date(Date.UTC(selectedYear, selectedMonth - 1, 0, 0, 0, 0)).getUTCDate();

    const days: CalendarDay[] = [];

    for (let i = startDayOfWeek - 1; i >= 0; i--) {
      const d = daysInPrevMonth - i;
      const prevMonth = selectedMonth === 1 ? 12 : selectedMonth - 1;
      const prevYear = selectedMonth === 1 ? selectedYear - 1 : selectedYear;
      const dateKey = `${prevYear}-${String(prevMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      days.push({
        dateKey,
        year: prevYear,
        month: prevMonth,
        day: d,
        isCurrentMonth: false,
        isToday: dateKey === `${todayKst.year}-${String(todayKst.month).padStart(2, '0')}-${String(todayKst.date).padStart(2, '0')}`,
        dayOfWeek: days.length % 7,
      });
    }

    for (let d = 1; d <= daysInMonth; d++) {
      const dateKey = `${selectedYear}-${String(selectedMonth).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      days.push({
        dateKey,
        year: selectedYear,
        month: selectedMonth,
        day: d,
        isCurrentMonth: true,
        isToday: dateKey === `${todayKst.year}-${String(todayKst.month).padStart(2, '0')}-${String(todayKst.date).padStart(2, '0')}`,
        dayOfWeek: days.length % 7,
      });
    }

    let nextDay = 1;
    while (days.length % 7 !== 0) {
      const nextMonth = selectedMonth === 12 ? 1 : selectedMonth + 1;
      const nextYear = selectedMonth === 12 ? selectedYear + 1 : selectedYear;
      const dateKey = `${nextYear}-${String(nextMonth).padStart(2, '0')}-${String(nextDay).padStart(2, '0')}`;
      days.push({
        dateKey,
        year: nextYear,
        month: nextMonth,
        day: nextDay++,
        isCurrentMonth: false,
        isToday: dateKey === `${todayKst.year}-${String(todayKst.month).padStart(2, '0')}-${String(todayKst.date).padStart(2, '0')}`,
        dayOfWeek: days.length % 7,
      });
    }

    const weeks: CalendarDay[][] = [];
    for (let i = 0; i < days.length; i += 7) {
      weeks.push(days.slice(i, i + 7));
    }
    return weeks;
  }, [selectedYear, selectedMonth, todayKst]);

  const daySessionsMap = useMemo(() => {
    const map = new Map<string, CalendarSessionDto[]>();
    const now = new Date();

    sessions.forEach((s) => {
      const sStart = parseKstInstant(s.startedAt);
      const sEnd = s.endedAt ? parseKstInstant(s.endedAt) : now;
      const startKst = getKstDate(sStart);
      const endKst = getKstDate(sEnd);

      const startDateOnly = new Date(Date.UTC(startKst.year, startKst.month - 1, startKst.date));
      const endDateOnly = new Date(Date.UTC(endKst.year, endKst.month - 1, endKst.date));

      const cur = new Date(startDateOnly);
      while (cur <= endDateOnly) {
        const kst = getKstDate(cur);
        const key = `${kst.year}-${String(kst.month).padStart(2, '0')}-${String(kst.date).padStart(2, '0')}`;
        if (!map.has(key)) {
          map.set(key, []);
        }
        const list = map.get(key)!;
        if (!list.some((existing) => existing.sessionId === s.sessionId)) {
          list.push(s);
        }
        cur.setUTCDate(cur.getUTCDate() + 1);
      }
    });

    return map;
  }, [sessions]);

  const weekSegmentsList = useMemo(() => {
    const result: WeekSegment[][] = [];

    calendarWeeks.forEach((weekDays) => {
      const repSessions: (CalendarSessionDto | null)[] = weekDays.map((day) => {
        const daySessions = daySessionsMap.get(day.dateKey) || [];
        if (daySessions.length === 0) return null;
        const liveSession = daySessions.find((s) => s.isLive);
        if (liveSession) return liveSession;
        return [...daySessions].sort((a, b) => b.durationSeconds - a.durationSeconds)[0];
      });

      const segments: WeekSegment[] = [];
      const DAY_WIDTH_PERCENT = 100 / 7;

      let segStartIdx = -1;
      let currentSession: CalendarSessionDto | null = null;

      const flushSegment = (startIdx: number, endIdx: number, session: CalendarSessionDto) => {
        const leftPercent = startIdx * DAY_WIDTH_PERCENT + 0.4;
        const spanDays = endIdx - startIdx + 1;
        const widthPercent = spanDays * DAY_WIDTH_PERCENT - 0.8;

        const sStart = parseKstInstant(session.startedAt);
        const sEnd = session.endedAt ? parseKstInstant(session.endedAt) : new Date();
        const startKst = getKstDate(sStart);
        const endKst = getKstDate(sEnd);

        const startDayObj = weekDays[startIdx];
        const endDayObj = weekDays[endIdx];

        const isStartOfSession =
          startKst.year === startDayObj.year &&
          startKst.month === startDayObj.month &&
          startKst.date === startDayObj.day;

        const isEndOfSession =
          !session.isLive &&
          endKst.year === endDayObj.year &&
          endKst.month === endDayObj.month &&
          endKst.date === endDayObj.day;

        const palette = session.isLive ? LIVE_PALETTE : DEFAULT_PALETTE;

        let barLabel = '';
        if (session.isLive) {
          const cat = session.categoryName ? ` · ${session.categoryName}` : '';
          barLabel = widthPercent > 4.5 ? `🔴 LIVE${cat}` : '🔴 LIVE';
        } else {
          barLabel = session.categoryName || '기타';
        }

        segments.push({
          session,
          palette,
          leftPercent,
          widthPercent,
          isStartOfSession,
          isEndOfSession,
          laneIndex: 0,
          barLabel,
        });
      };

      for (let i = 0; i < 7; i++) {
        const s = repSessions[i];
        if (!currentSession && s) {
          segStartIdx = i;
          currentSession = s;
        } else if (currentSession && (!s || s.sessionId !== currentSession.sessionId)) {
          flushSegment(segStartIdx, i - 1, currentSession);
          if (s) {
            segStartIdx = i;
            currentSession = s;
          } else {
            segStartIdx = -1;
            currentSession = null;
          }
        }
      }

      if (currentSession && segStartIdx !== -1) {
        flushSegment(segStartIdx, 6, currentSession);
      }

      result.push(segments);
    });

    return result;
  }, [calendarWeeks, daySessionsMap]);

  const handleMouseMove = (e: React.MouseEvent, session: CalendarSessionDto, palette: SessionPalette) => {
    setHoveredSession({
      session,
      palette,
      x: e.clientX,
      y: e.clientY,
    });
  };

  const handleMouseLeave = () => {
    setHoveredSession(null);
  };

  const handleBarClick = (e: React.MouseEvent, session: CalendarSessionDto, palette: SessionPalette) => {
    e.stopPropagation();
    setHoveredSession({
      session,
      palette,
      x: e.clientX,
      y: e.clientY,
    });
  };

  const handleExtraBadgeClick = (e: React.MouseEvent, dateKey: string, dayNum: number, daySessions: CalendarSessionDto[]) => {
    e.stopPropagation();
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setPopoverDate({
      dateKey,
      dayNum,
      sessions: daySessions,
      x: rect.left,
      y: rect.bottom + 6,
    });
  };

  useEffect(() => {
    const handleClickOutside = () => {
      if (popoverDate) setPopoverDate(null);
      if (hoveredSession) setHoveredSession(null);
    };
    window.addEventListener('click', handleClickOutside);
    return () => window.removeEventListener('click', handleClickOutside);
  }, [popoverDate, hoveredSession]);

  return (
    <div className="w-full bg-[#141416] border border-[#2A2A2C] rounded-2xl p-4 sm:p-5 shadow-xl transition-all" ref={containerRef}>
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[#2A2A2C]">
        <div className="flex items-center gap-2.5">
          <span className="text-base sm:text-lg font-bold text-white flex items-center gap-1.5">
            <span>📅</span> 월간 방송 타임라인
          </span>
          <span className="text-xs font-mono text-gray-100">
            {selectedYear}.{String(selectedMonth).padStart(2, '0')}
          </span>
        </div>

        <div className="flex items-center gap-1.5 bg-[#1a1a1c] border border-[#2A2A2C] rounded-lg p-1">
          <button
            onClick={handlePrevMonth}
            className="p-1 rounded hover:bg-[#2A2A2C] text-gray-100 hover:text-white transition-colors"
            title="이전 달"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span className="text-xs font-bold text-white px-2">
            {selectedYear}년 {selectedMonth}월
          </span>
          <button
            onClick={handleNextMonth}
            className="p-1 rounded hover:bg-[#2A2A2C] text-gray-100 hover:text-white transition-colors"
            title="다음 달"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
          <button
            onClick={handleGoToday}
            className="ml-1 text-[11px] font-semibold px-2 py-0.5 rounded bg-[#2A2A2C] hover:bg-[#38383b] text-gray-200 hover:text-white transition-colors"
          >
            오늘
          </button>
        </div>
      </div>

      <div className="mt-3.5 overflow-x-auto">
        <div className="min-w-[640px] border border-[#2A2A2C] rounded-xl overflow-hidden bg-[#101012]">
          <div className="grid grid-cols-7 text-center py-1.5 bg-[#18181b] border-b border-[#2A2A2C] text-[11px] font-semibold text-gray-100">
            <div className="text-rose-500">일</div>
            <div>월</div>
            <div>화</div>
            <div>수</div>
            <div>목</div>
            <div>금</div>
            <div className="text-[#38bdf8]">토</div>
          </div>

          {loading ? (
            <div className="p-12 text-center text-sm text-gray-200 flex flex-col items-center justify-center gap-2">
              <div className="w-6 h-6 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
              <span className="font-medium">방송 세션 타임라인을 불러오는 중...</span>
            </div>
          ) : error ? (
            <div className="p-8 text-center text-sm text-rose-400">{error}</div>
          ) : (
            <div className="divide-y divide-[#2A2A2C]">
              {calendarWeeks.map((week, wIdx) => {
                const weekSegments = weekSegmentsList[wIdx] || [];
                const rowHeightPx = 54;

                return (
                  <div
                    key={`week-${wIdx}`}
                    className="relative grid grid-cols-7 divide-x divide-[#2A2A2C]"
                    style={{ minHeight: `${rowHeightPx}px` }}
                  >
                    {week.map((day, dIdx) => {
                      const allDaySessions = daySessionsMap.get(day.dateKey) || [];
                      const extraCount = allDaySessions.length > 1 ? allDaySessions.length - 1 : 0;

                      return (
                        <div
                          key={day.dateKey}
                          className={`p-1.5 relative flex flex-col justify-start transition-colors ${
                            !day.isCurrentMonth
                              ? 'bg-[#0d0d0f]/60 text-gray-200'
                              : 'bg-[#141416] text-gray-200 hover:bg-[#18181b]'
                          } ${day.isToday ? 'bg-[#1e293b]/25 ring-1 ring-inset ring-[#00FFA3]/30' : ''}`}
                        >
                          <div className="flex items-center justify-between w-full z-10 relative leading-none">
                            <span
                              className={`text-[11px] font-medium leading-none ${
                                day.isToday
                                  ? 'w-5 h-5 rounded-full bg-[#00FFA3] text-black font-bold flex items-center justify-center shadow-[0_0_8px_rgba(0,255,163,0.4)]'
                                  : dIdx === 0
                                  ? 'text-rose-500'
                                  : dIdx === 6
                                  ? 'text-[#38bdf8]'
                                  : ''
                              }`}
                            >
                              {day.day}
                            </span>

                            {extraCount > 0 && day.isCurrentMonth && (
                              <button
                                type="button"
                                onClick={(e) => handleExtraBadgeClick(e, day.dateKey, day.day, allDaySessions)}
                                className="text-[9.5px] font-bold px-1.5 py-0.5 rounded bg-[#27272a] hover:bg-[#00FFA3] hover:text-black text-[#00FFA3] border border-[#3f3f46] cursor-pointer transition-all shadow-sm flex items-center gap-0.5"
                                title="전체 방송 세션 보기"
                              >
                                +{extraCount}개
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })}

                    <div className="absolute inset-0 pointer-events-none">
                      {weekSegments.map((seg, sIdx) => {
                        const topPx = 25;
                        const roundedClass = `${seg.isStartOfSession ? 'rounded-l-[5px]' : 'rounded-l-none'} ${
                          seg.isEndOfSession ? 'rounded-r-[5px]' : 'rounded-r-none'
                        }`;

                        return (
                          <div
                            key={`seg-${wIdx}-${sIdx}`}
                            className={`track-bar pointer-events-auto absolute h-[22px] cursor-pointer flex items-center justify-center px-2 shadow-sm border overflow-hidden transition-all ${
                              seg.palette.borderColor
                            } bg-gradient-to-r ${seg.palette.gradient} ${roundedClass} ${
                              seg.session.isLive ? 'animate-pulse' : ''
                            }`}
                            style={{
                              left: `${seg.leftPercent}%`,
                              width: `${seg.widthPercent}%`,
                              top: `${topPx}px`,
                              boxShadow: `0 0 8px ${seg.palette.glow}`,
                            }}
                            onMouseMove={(e) => handleMouseMove(e, seg.session, seg.palette)}
                            onMouseLeave={handleMouseLeave}
                            onClick={(e) => handleBarClick(e, seg.session, seg.palette)}
                          >
                              {seg.barLabel && (
                                <span className="font-bold text-white text-[10.5px] leading-none tracking-tight drop-shadow-sm flex items-center justify-center gap-1 whitespace-nowrap overflow-hidden text-ellipsis">
                                  {seg.session.isLive ? (
                                    <span className="w-1.5 h-1.5 rounded-full bg-rose-400 shrink-0 animate-ping" />
                                  ) : (
                                    <span className="w-1.5 h-1.5 rounded-full bg-[#00FFA3] shrink-0" />
                                  )}
                                  <span className="truncate">{seg.barLabel}</span>
                                </span>
                              )}
                            </div>
                          );
                        })}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {hoveredSession && (
        <div
          className="fixed pointer-events-none z-50 bg-[#1a1a1c]/95 backdrop-blur-md border border-[#3f3f46] rounded-xl p-3 shadow-2xl text-xs w-auto max-w-xs min-w-[280px]"
          style={{
            left: `${Math.min(window.innerWidth - 300, hoveredSession.x + 12)}px`,
            top: `${Math.min(window.innerHeight - 180, hoveredSession.y + 12)}px`,
          }}
        >
          <div className="font-bold flex items-center gap-1.5 text-xs mb-1">
            <span className={`w-2 h-2 rounded-full ${hoveredSession.palette.badgeBg}`} />
            <span className={hoveredSession.palette.textColor}>
              {hoveredSession.session.categoryName || '카테고리 없음'}
            </span>
            {hoveredSession.session.isLive && (
              <span className="ml-auto text-[10px] px-1.5 py-0.5 rounded bg-rose-500/20 text-rose-400 font-bold border border-rose-500/30">
                LIVE
              </span>
            )}
          </div>
          <div className="font-bold text-white text-xs leading-snug line-clamp-2 mb-2">
            {hoveredSession.session.title || '제목 없음'}
          </div>
          <div className="space-y-1.5 border-t border-[#2A2A2C] pt-2 text-gray-200 text-[11px]">
            <div className="flex justify-between items-center gap-4">
              <span className="text-gray-100 shrink-0">⏱️ 방송 시간</span>
              <span className="font-semibold text-white whitespace-nowrap text-right">
                {formatSessionTimeRange(
                  parseKstInstant(hoveredSession.session.startedAt),
                  hoveredSession.session.endedAt ? parseKstInstant(hoveredSession.session.endedAt) : null
                )}{' '}
                ({formatDurationHours(hoveredSession.session.durationSeconds)})
              </span>
            </div>
            <div className="flex justify-between items-center gap-4">
              <span className="text-gray-100 shrink-0">👥 평균 / 피크</span>
              <span className="font-semibold text-[#38bdf8] whitespace-nowrap text-right">
                {hoveredSession.session.averageViewers.toLocaleString()}명 /{' '}
                {hoveredSession.session.peakViewers.toLocaleString()}명
              </span>
            </div>
          </div>
        </div>
      )}

      {popoverDate && (
        <div
          className="fixed z-50 bg-[#18181b] border border-[#3f3f46] rounded-xl p-3 shadow-2xl text-xs max-w-sm w-72"
          style={{
            left: `${Math.min(window.innerWidth - 300, Math.max(16, popoverDate.x))}px`,
            top: `${Math.min(window.innerHeight - 250, popoverDate.y)}px`,
          }}
          onClick={(e) => e.stopPropagation()}
        >
          <div className="flex items-center justify-between pb-2 mb-2 border-b border-[#2A2A2C]">
            <span className="font-bold text-white text-xs flex items-center gap-1.5">
              <span>📅</span> {popoverDate.dateKey} ({popoverDate.sessions.length}개 세션)
            </span>
            <button
              onClick={() => setPopoverDate(null)}
              className="text-gray-100 hover:text-white p-0.5 rounded hover:bg-[#2A2A2C]"
            >
              ✕
            </button>
          </div>
          <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
            {popoverDate.sessions.map((s) => {
              const palette = s.isLive ? LIVE_PALETTE : DEFAULT_PALETTE;
              return (
                <div key={s.sessionId} className="p-2 rounded-lg bg-[#202024] border border-[#2e2e32] text-[11px] space-y-1">
                  <div className="flex items-center justify-between">
                    <span className={`font-bold ${palette.textColor}`}>
                      {s.categoryName || '카테고리 없음'}
                    </span>
                    <span className="text-gray-100 font-mono text-[10px]">
                      {formatDurationHours(s.durationSeconds)}
                    </span>
                  </div>
                  <div className="text-white font-medium line-clamp-1">{s.title || '제목 없음'}</div>
                  <div className="text-gray-100 text-[10px] flex justify-between">
                    <span>
                      {formatSessionTimeRange(
                        parseKstInstant(s.startedAt),
                        s.endedAt ? parseKstInstant(s.endedAt) : null
                      )}
                    </span>
                    <span className="text-[#38bdf8]">
                      평균 {s.averageViewers.toLocaleString()}명
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
