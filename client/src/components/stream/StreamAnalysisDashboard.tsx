import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { AnalysisTabs, type DashboardSessionTab } from './dashboard/AnalysisTabs';
import { AnalysisChart } from './dashboard/AnalysisChart';
import { HighlightSection } from './dashboard/HighlightSection';
import { SessionSummaryGrid } from './dashboard/SessionSummaryGrid';
import { DashboardHeader } from './dashboard/DashboardHeader';
import { StreamProfileHeader } from './dashboard/StreamProfileHeader';
import { useHighlights } from '../../hooks/useHighlights';
import type { StreamSegment } from '../../types/StreamSegment';
import type { StreamerInfo } from '../../types/StreamerInfo';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const CONFIG = {
  HIGHLIGHT_POLLING_INTERVAL: 10000,
  LIVE_REFRESH_INTERVAL: 30000,
};

const getRelativeLabel = (isoString: string) => {
  try {
    const target = new Date(isoString);
    const now = new Date();

    const targetDate = new Date(target.getFullYear(), target.getMonth(), target.getDate());
    const nowDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());

    const diffTime = nowDate.getTime() - targetDate.getTime();
    const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return "오늘";
    if (diffDays === 1) return "어제";
    if (diffDays === 2) return "이틀 전";
    return `${diffDays}일 전`;
  } catch (e) {
    return "과거";
  }
};

const formatSessionDate = (isoString: string) => {
  try {
    const d = new Date(isoString);
    const month = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    const relative = getRelativeLabel(isoString);
    return `${month}.${day} (${relative})`;
  } catch (e) {
    return "과거 방송";
  }
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedSessionId = searchParams.get('sessionId');

  const [selectedTab, setSelectedTab] = useState<string>("");
  const [segments, setSegments] = useState<StreamSegment[]>([]);
  const [availableSessions, setAvailableSessions] = useState<DashboardSessionTab[]>([]);

  const [streamerInfo, setStreamerInfo] = useState<StreamerInfo | null>(null);
  const [isLive, setIsLive] = useState(false);
  const [historicalData, setHistoricalData] = useState<any[]>([]);
  const [historicalTimeline, setHistoricalTimeline] = useState<any[]>([]);
  const [isHistoryLoading, setIsHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyIsAdult, setHistoryIsAdult] = useState(false);

  const [maxY, setMaxY] = useState(10);
  const [maxViewerY, setMaxViewerY] = useState(100);
  const [hoveredData, setHoveredData] = useState<{ value: number | null; viewers: number | null; time: string | null }>({
    value: null, viewers: null, time: null,
  });

  const visibleSessions = useMemo(() => {
    if (requestedSessionId) {
      return availableSessions.filter((session) =>
        session.sessionId === requestedSessionId || session.linkedSessionIds?.includes(requestedSessionId)
      );
    }

    if (isLive) {
      return availableSessions.filter((session) => session.isLive);
    }

    return availableSessions.slice(0, 1);
  }, [availableSessions, isLive, requestedSessionId]);

  const currentSessionInfo = visibleSessions.find(s => s.sessionId === selectedTab);
  const isLiveTabSelected = currentSessionInfo?.isLive === true;

  const matchViewerCount = (
    targetTs: number,
    timeline: Array<{ timestamp: number; viewerCount: number }> = [],
    fallbackViewer: number = 0
  ) => {
    if (!timeline || timeline.length === 0) return fallbackViewer;
    const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);
    const targetMs = normalize(targetTs);

    let matched = fallbackViewer;
    for (let i = 0; i < timeline.length; i++) {
      const itemMs = normalize(timeline[i].timestamp);
      if (itemMs <= targetMs) {
        matched = timeline[i].viewerCount;
      } else {
        break;
      }
    }
    if (matched === fallbackViewer && timeline.length > 0 && targetMs < normalize(timeline[0].timestamp)) {
      matched = timeline[0].viewerCount;
    }
    return matched;
  };

  const compressHistoryData = (
    data: any[],
    timeline: any[],
    matchViewer: (targetTs: number, timeline: any[], fallbackViewer?: number) => number
  ) => {
    if ((!data || data.length === 0) && (!timeline || timeline.length === 0)) {
      return [];
    }

    if (data && data.length > 0) {
      return data.map((p: any) => ({
        ...p,
        viewerCount: matchViewer(p.timestamp, timeline, p.viewerCount || 0),
        hasFirepower: true
      }));
    }

    return (timeline || []).map((t: any) => ({
      timestamp: t.timestamp,
      value: 0,
      status: 'NORMAL',
      viewerCount: t.viewerCount,
      hasFirepower: false
    }));
  };

  const fetchSessionHistory = useCallback(async (sessionId: string, isBackground = false, signal?: AbortSignal) => {
    if (!sessionId || !streamId) return;
    if (!isBackground) setIsHistoryLoading(true);

    try {
      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/history?sessionId=${sessionId}`, { signal });
      if (!res.ok) throw new Error("분석 데이터를 불러오지 못했습니다.");
      const data = await res.json();
      const sortedHistory = (data.dataPoints || []).sort((a: any, b: any) => a.timestamp - b.timestamp);
      setHistoricalData(sortedHistory);
      setHistoricalTimeline(data.timeline || []);
      setSegments(data.segments || []);
      setHistoryIsAdult(Boolean(data.isAdult));
      setHistoryError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      console.error("분석 데이터 조회 실패", err);
      if (!isBackground) {
        setHistoryError(err instanceof Error ? err.message : "네트워크 오류");
        setHistoricalData([]);
        setHistoricalTimeline([]);
        setSegments([]);
        setHistoryIsAdult(false);
      }
    } finally {
      if (!isBackground && (!signal || !signal.aborted)) {
        setIsHistoryLoading(false);
      }
    }
  }, [streamId]);

  const { highlights } = useHighlights(
    streamId || "",
    isLiveTabSelected ? "realtime" : selectedTab,
    CONFIG.HIGHLIGHT_POLLING_INTERVAL
  );

  useEffect(() => {
    if (!streamId) return;
    fetch(`${API_BASE_URL}/api/v1/streams/${streamId}`)
      .then(res => res.ok ? res.json() : null)
      .then(data => {
        if (data) {
          setStreamerInfo(data);
          setIsLive(data.status !== 'OFFLINE');
        }
      })
      .catch(err => console.error("스트리머 정보를 불러오는데 실패했습니다.", err));
  }, [streamId]);

  useEffect(() => {
    if (!streamId) return;
    const sessionLimit = requestedSessionId ? 1000 : 10;
    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/available-sessions?limit=${sessionLimit}`)
      .then(res => res.ok ? res.json() : [])
      .then((sessions: any[]) => {
        const isCurrentlyLive = streamerInfo?.status !== 'OFFLINE';

        if (isCurrentlyLive && sessions.length > 0) {
          const liveSession: DashboardSessionTab = {
            sessionId: sessions[0].sessionId,
            label: "🔴 현재 방송 (LIVE)",
            isLive: true,
            liveTitle: streamerInfo?.liveTitle ?? sessions[0].liveTitle ?? sessions[0].title ?? undefined,
            categoryName: streamerInfo?.categoryName ?? sessions[0].categoryName ?? undefined,
            viewers: streamerInfo?.concurrentUserCount,
            startedAt: sessions[0].startedAt,
            endedAt: sessions[0].endedAt,
            averageViewerCount: sessions[0].averageViewerCount,
            peakViewers: sessions[0].peakViewers,
            subscriberChatRatio: sessions[0].subscriberChatRatio,
            isAdult: sessions[0].isAdult,
            linkedSessionIds: sessions[0].linkedSessionIds
          };

          const pastSessions: DashboardSessionTab[] = sessions.slice(1).map(s => ({
            sessionId: s.sessionId,
            label: `📁 ${formatSessionDate(s.startedAt)}`,
            isLive: false,
            liveTitle: s.liveTitle ?? s.title,
            categoryName: s.categoryName || "종합 게임",
            viewers: 0,
            startedAt: s.startedAt,
            endedAt: s.endedAt,
            averageViewerCount: s.averageViewerCount,
            peakViewers: s.peakViewers,
            subscriberChatRatio: s.subscriberChatRatio,
            isAdult: s.isAdult,
            linkedSessionIds: s.linkedSessionIds
          }));

          setAvailableSessions([liveSession, ...pastSessions]);
        } else if (!isCurrentlyLive && sessions.length > 0) {
          const pastSessions: DashboardSessionTab[] = sessions.map(s => ({
            sessionId: s.sessionId,
            label: `📁 ${formatSessionDate(s.startedAt)}`,
            isLive: false,
            liveTitle: s.liveTitle ?? s.title,
            categoryName: s.categoryName || "종합 게임",
            viewers: 0,
            startedAt: s.startedAt,
            endedAt: s.endedAt,
            averageViewerCount: s.averageViewerCount,
            peakViewers: s.peakViewers,
            subscriberChatRatio: s.subscriberChatRatio,
            isAdult: s.isAdult,
            linkedSessionIds: s.linkedSessionIds
          }));
          setAvailableSessions(pastSessions);
        } else if (isCurrentlyLive && sessions.length === 0) {
          setAvailableSessions([{
            sessionId: "realtime",
            label: "🔴 현재 방송 (LIVE)",
            isLive: true,
            liveTitle: streamerInfo?.liveTitle ?? undefined,
            categoryName: streamerInfo?.categoryName ?? undefined,
            viewers: streamerInfo?.concurrentUserCount
          }]);
        } else {
          setAvailableSessions([]);
        }
      })
      .catch(err => console.error("세션 목록 로드 실패", err));
  }, [streamId, streamerInfo, requestedSessionId]);

  useEffect(() => {
    if (visibleSessions.length > 0) {
      const exists = visibleSessions.some(s => s.sessionId === selectedTab);
      if (!exists) {
        setSelectedTab(visibleSessions[0].sessionId);
      }
    }
  }, [visibleSessions, selectedTab]);

  useEffect(() => {
    if (!selectedTab) return;
    const controller = new AbortController();

    fetchSessionHistory(selectedTab, false, controller.signal);

    let timer: ReturnType<typeof setInterval> | null = null;
    if (isLiveTabSelected) {
      timer = setInterval(() => {
        fetchSessionHistory(selectedTab, true, controller.signal);
      }, CONFIG.LIVE_REFRESH_INTERVAL);
    }

    return () => {
      controller.abort();
      if (timer) clearInterval(timer);
    };
  }, [selectedTab, isLiveTabSelected, fetchSessionHistory]);

  const compressedHistory = useMemo(() => {
    return compressHistoryData(historicalData, historicalTimeline, matchViewerCount);
  }, [historicalData, historicalTimeline]);

  useEffect(() => {
    if (compressedHistory.length > 0) {
      const currentMax = Math.max(...compressedHistory.map((d: any) => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax + 5);

      const currentMaxViewer = Math.max(...compressedHistory.map((d: any) => d.viewerCount || 0));
      if (currentMaxViewer > 0) {
        setMaxViewerY(Math.ceil(currentMaxViewer * 1.15));
      }
    }
  }, [compressedHistory, maxY]);

  const formatTime = (ts: any) => {
    if (!ts) return "";
    let d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  const chartDisplayData = useMemo(() => {
    return compressedHistory.map((d: any, idx: number) => ({
      ...d,
      slotIndex: idx,
      hasData: true
    }));
  }, [compressedHistory]);

  const rebangIndexes = useMemo(() => {
    if (compressedHistory.length === 0) return [];
    const indexes: number[] = [];
    for (let i = 1; i < compressedHistory.length; i++) {
      const prev = compressedHistory[i - 1];
      const curr = compressedHistory[i];
      const timeDiff = curr.timestamp - prev.timestamp;

      if (timeDiff >= 120000 || (curr.offsetMs !== undefined && prev.offsetMs !== undefined && curr.offsetMs < prev.offsetMs)) {
        indexes.push(i);
      }
    }
    return indexes;
  }, [compressedHistory]);

  const handleMouseMove = (state: any) => {
    if (state?.activePayload?.[0]?.payload?.hasData) {
      const p = state.activePayload[0].payload;
      setHoveredData({
        value: p.value,
        viewers: p.viewerCount !== undefined ? p.viewerCount : null,
        time: formatTime(p.timestamp)
      });
    }
  };

  const isLiveSession = isLiveTabSelected || !!currentSessionInfo?.isLive;
  const displayLabel = currentSessionInfo ? currentSessionInfo.label.replace(/^[^\w\s가-힣0-9.-]+\s*/, '') : "해당 방송";

  const viewerMetric = useMemo(() => {
    if (hoveredData.viewers !== null && hoveredData.viewers !== undefined) {
      return { label: "해당 시점 시청자", value: hoveredData.viewers };
    }

    const maxViewer = compressedHistory.length > 0
      ? Math.max(...compressedHistory.map((d: any) => d.viewerCount || 0))
      : (isLiveTabSelected ? (streamerInfo?.concurrentUserCount || 0) : (currentSessionInfo?.viewers || 0));

    const label = isLiveSession ? "실시간 최고 시청자" : `${displayLabel} 최고 시청자`;
    return { label, value: maxViewer };
  }, [compressedHistory, hoveredData, isLiveTabSelected, isLiveSession, streamerInfo?.concurrentUserCount, currentSessionInfo, displayLabel]);

  const metric = useMemo(() => {
    if (hoveredData.value !== null) {
      return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };
    }

    const maxVal = compressedHistory.length > 0
      ? Math.max(...compressedHistory.map((d: any) => d.value || 0))
      : 0;

    const label = isLiveSession ? "실시간 최고 화력" : `${displayLabel} 최고 화력`;
    return {
      label,
      value: maxVal
    };
  }, [compressedHistory, hoveredData, isLiveSession, displayLabel]);

  const displayTitle = isLiveTabSelected ? streamerInfo?.liveTitle : currentSessionInfo?.liveTitle;
  const displayCategory = isLiveTabSelected ? streamerInfo?.categoryName : currentSessionInfo?.categoryName;
  const displayViewers = isLiveTabSelected ? streamerInfo?.concurrentUserCount : currentSessionInfo?.viewers;
  const isAdultSession = Boolean(historyIsAdult || currentSessionInfo?.isAdult || segments.some(s => s.isAdult));

  if (!streamId) return <div className="p-10 text-center text-gray-100">잘못된 접근입니다.</div>;

  const isHistoryEmpty = !isHistoryLoading && historicalData.length === 0 && historicalTimeline.length === 0;

  return (
    <div className="w-full pb-16 sm:pb-20 bg-[#060606] min-h-screen text-white px-2 sm:px-6 lg:px-8">
      <DashboardHeader onBack={() => navigate(-1)} />
      <StreamProfileHeader
        streamId={streamId}
        streamerName={streamerInfo?.streamerName}
        profileImageUrl={streamerInfo?.profileImageUrl}
        isLive={isLive}
        isLiveTab={isLiveTabSelected}
        status={streamerInfo?.status}
        viewers={displayViewers}
        liveTitle={displayTitle}
        categoryName={displayCategory}
      />

      <AnalysisTabs
        availableSessions={visibleSessions}
        selected={selectedTab}
        onSelect={(tab) => {
          setSelectedTab(tab);
          setHoveredData({ value: null, viewers: null, time: null });
        }}
      />

      {isAdultSession && (
        <div className="mb-6 p-4 rounded-2xl bg-rose-950/20 border border-rose-500/30 flex items-start gap-3.5 backdrop-blur-md">
          <span className="text-2xl shrink-0">🔞</span>
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <h4 className="text-rose-400 font-bold text-sm sm:text-base">연령 제한(19금) 설정 구간 안내</h4>
              <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-rose-500/20 text-rose-300 border border-rose-500/30">
                채팅 화력 수집 제외
              </span>
            </div>
            <p className="text-gray-300 text-xs sm:text-sm leading-relaxed">
              치지직 정책상 연령 제한(19금) 방송 구간은 채팅 접근이 제한되어 <strong>채팅 화력 그래프 및 실시간 하이라이트 감지</strong>가 제공되지 않습니다.
              <span className="text-gray-400 block sm:inline sm:ml-1">
                (시청자 수 추이, 방송 세션 및 총 방송 시간 통계는 정상 제공됩니다)
              </span>
            </p>
          </div>
        </div>
      )}

      <AnalysisChart
        chartData={chartDisplayData}
        metric={metric}
        viewerMetric={viewerMetric}
        maxY={maxY}
        maxViewerY={maxViewerY}
        isLoading={isHistoryLoading}
        isGathering={false}
        error={historyError}
        selectedTab={isLiveTabSelected ? "realtime" : selectedTab}
        historyEmpty={isHistoryEmpty}
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setHoveredData({ value: null, viewers: null, time: null })}
        formatTime={formatTime}
        rebangIndexes={rebangIndexes}
        segments={segments}
        highlights={highlights}
      />

      <SessionSummaryGrid
        isLive={isLiveTabSelected}
        startedAt={isLiveTabSelected ? availableSessions[0]?.startedAt : currentSessionInfo?.startedAt}
        endedAt={isLiveTabSelected ? null : currentSessionInfo?.endedAt}
        currentViewers={isLiveTabSelected ? (streamerInfo?.concurrentUserCount || 0) : 0}
        timeline={historicalTimeline}
        dataPoints={historicalData}
        summaryAvg={currentSessionInfo?.averageViewerCount}
        summaryPeak={currentSessionInfo?.peakViewers}
        subscriberChatRate={currentSessionInfo?.subscriberChatRatio ?? null}
      />

      <HighlightSection
        highlights={highlights}
        selectedTab={isLiveTabSelected ? "realtime" : selectedTab}
        startedAt={isLiveTabSelected ? availableSessions[0]?.startedAt : currentSessionInfo?.startedAt}
        endedAt={isLiveTabSelected ? null : currentSessionInfo?.endedAt}
        isAdult={isAdultSession}
      />

      <footer className="mt-16 sm:mt-24 pt-8 sm:pt-12 border-t border-gray-800/60 text-center">
        <div className="mb-6">
          <span className="text-[#00FFA3] font-black text-xl italic tracking-tighter uppercase">Cheese Pick</span>
        </div>
        <a
          href="https://forms.gle/hUkZBr9KCTDyTXLW9"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 text-gray-100 hover:text-[#00FFA3] transition-all text-sm font-bold bg-[#1a1a1c] px-8 py-3.5 rounded-full border border-gray-800 hover:border-[#00FFA3]/50 shadow-xl group"
        >
          💡 치즈픽 하이라이트 엔진 피드백 보내기
          <svg className="w-4 h-4 transform group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
          </svg>
        </a>
        <p className="mt-8 text-[11px] text-gray-200 font-medium tracking-widest uppercase">
          © 2026 CheesePick. Advanced Stream Analytics Pipeline.
        </p>
      </footer>
    </div>
  );
};
