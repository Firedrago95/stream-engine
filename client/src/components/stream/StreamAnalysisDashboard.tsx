import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { AnalysisTabs, type DashboardSessionTab } from './dashboard/AnalysisTabs';
import { AnalysisChart } from './dashboard/AnalysisChart';
import { HighlightSection } from './dashboard/HighlightSection';
import { SessionSummaryGrid } from './dashboard/SessionSummaryGrid';
import { DashboardHeader } from './dashboard/DashboardHeader';
import { StreamProfileHeader } from './dashboard/StreamProfileHeader';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';
import { useHighlights } from '../../hooks/useHighlights';
import type { StreamSegment } from '../../types/StreamSegment';
import type { StreamerInfo } from '../../types/StreamerInfo';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const CONFIG = {
  POLLING_INTERVAL: 3000,
  FIREPOWER_POLLING_INTERVAL: 3000,
  HIGHLIGHT_POLLING_INTERVAL: 10000,
  DISPLAY_POINTS: 60,
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
  const [maxY, setMaxY] = useState(10);
  const [maxViewerY, setMaxViewerY] = useState(100);
  const [hoveredData, setHoveredData] = useState<{ value: number | null; viewers: number | null; time: string | null }>({
    value: null, viewers: null, time: null,
  });

  const [liveTimeframe, setLiveTimeframe] = useState<'realtime' | 'cumulative'>('realtime');
  const [liveCumulativeData, setLiveCumulativeData] = useState<any[]>([]);
  const [liveCumulativeTimeline, setLiveCumulativeTimeline] = useState<any[]>([]);
  const [liveCumulativeSegments, setLiveCumulativeSegments] = useState<StreamSegment[]>([]);
  const [isLiveCumulativeLoading, setIsLiveCumulativeLoading] = useState(false);

  const visibleSessions = useMemo(() => {
    if (requestedSessionId) {
      return availableSessions.filter((session) => session.sessionId === requestedSessionId);
    }

    if (isLive) {
      return availableSessions.filter((session) => session.isLive);
    }

    return availableSessions.slice(0, 1);
  }, [availableSessions, isLive, requestedSessionId]);

  const currentSessionInfo = visibleSessions.find(s => s.sessionId === selectedTab);
  const isLiveTabSelected = currentSessionInfo?.isLive === true;

  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
    streamId || '',
    CONFIG.FIREPOWER_POLLING_INTERVAL,
    { enabled: isLiveTabSelected }
  );

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
    if (!data || data.length === 0) return [];

    return data.map((p: any) => ({
      ...p,
      viewerCount: matchViewer(p.timestamp, timeline, p.viewerCount || 0)
    }));
  };

  const stableData = useMemo(() => {
    if (!analysisData) return [];
    let points = [];
    if (Array.isArray(analysisData)) {
      points = [...analysisData];
    } else if (analysisData.dataPoints) {
      points = [...analysisData.dataPoints];
    } else {
      points = Object.keys(analysisData)
        .filter(k => !isNaN(Number(k)))
        .map(k => analysisData[k]);
    }
    points.sort((a: any, b: any) => a.timestamp - b.timestamp);

    const timeline = analysisData.timeline || [];
    const fallbackViewers = streamerInfo?.concurrentUserCount || 0;

    return points.slice(-CONFIG.DISPLAY_POINTS).map((p: any) => ({
      ...p,
      viewerCount: matchViewerCount(p.timestamp, timeline, fallbackViewers)
    }));
  }, [analysisData, streamerInfo?.concurrentUserCount]);

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
            liveTitle: streamerInfo?.liveTitle ?? sessions[0].liveTitle ?? undefined,
            categoryName: streamerInfo?.categoryName ?? sessions[0].categoryName ?? undefined,
            viewers: streamerInfo?.concurrentUserCount,
            startedAt: sessions[0].startedAt,
            endedAt: sessions[0].endedAt,
            averageViewerCount: sessions[0].averageViewerCount,
            peakViewers: sessions[0].peakViewers,
            subscriberChatRatio: sessions[0].subscriberChatRatio
          };

          const pastSessions: DashboardSessionTab[] = sessions.slice(1).map(s => ({
            sessionId: s.sessionId,
            label: `📁 ${formatSessionDate(s.startedAt)}`,
            isLive: false,
            liveTitle: s.liveTitle,
            categoryName: s.categoryName || "종합 게임",
            viewers: 0,
            startedAt: s.startedAt,
            endedAt: s.endedAt,
            averageViewerCount: s.averageViewerCount,
            peakViewers: s.peakViewers,
            subscriberChatRatio: s.subscriberChatRatio
          }));

          setAvailableSessions([liveSession, ...pastSessions]);
        } else if (!isCurrentlyLive && sessions.length > 0) {
          const pastSessions: DashboardSessionTab[] = sessions.map(s => ({
            sessionId: s.sessionId,
            label: `📁 ${formatSessionDate(s.startedAt)}`,
            isLive: false,
            liveTitle: s.liveTitle,
            categoryName: s.categoryName || "종합 게임",
            viewers: 0,
            startedAt: s.startedAt,
            endedAt: s.endedAt,
            averageViewerCount: s.averageViewerCount,
            peakViewers: s.peakViewers,
            subscriberChatRatio: s.subscriberChatRatio
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
    if (isLiveTabSelected || !selectedTab || !streamId || selectedTab === 'realtime') {
      setHistoricalData([]);
      setHistoricalTimeline([]);
      setSegments([]);
      return;
    }

    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/history?sessionId=${selectedTab}`)
      .then(res => res.ok ? res.json() : { dataPoints: [] })
      .then(data => {
        const sortedHistory = (data.dataPoints || []).sort((a: any, b: any) => a.timestamp - b.timestamp);
        setHistoricalData(sortedHistory);
        setHistoricalTimeline(data.timeline || []);
        setSegments(data.segments || []);
      })
      .catch(err => {
        console.error("과거 데이터를 불러오지 못했습니다.", err);
        setHistoricalData([]);
        setHistoricalTimeline([]);
        setSegments([]);
      });
  }, [selectedTab, streamId, isLiveTabSelected]);

  // 실시간 탭에서 '전체 누적' 선택 시 단 1회만 fetch하여 프론트엔드 캐싱
  useEffect(() => {
    if (!isLiveTabSelected || liveTimeframe !== 'cumulative' || !streamId) return;
    if (liveCumulativeData.length > 0) return;

    const liveSessionId = currentSessionInfo?.sessionId;
    if (!liveSessionId || liveSessionId === 'realtime') return;

    setIsLiveCumulativeLoading(true);
    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/history?sessionId=${liveSessionId}`)
      .then(res => res.ok ? res.json() : { dataPoints: [] })
      .then(data => {
        const sortedHistory = (data.dataPoints || []).sort((a: any, b: any) => a.timestamp - b.timestamp);
        setLiveCumulativeData(sortedHistory);
        setLiveCumulativeTimeline(data.timeline || []);
        setLiveCumulativeSegments(data.segments || []);
      })
      .catch(err => {
        console.error("실시간 방송의 전체 누적 데이터를 불러오지 못했습니다.", err);
      })
      .finally(() => {
        setIsLiveCumulativeLoading(false);
      });
  }, [isLiveTabSelected, liveTimeframe, streamId, currentSessionInfo?.sessionId, liveCumulativeData.length]);

  const compressedHistory = useMemo(() => {
    return compressHistoryData(historicalData, historicalTimeline, matchViewerCount);
  }, [historicalData, historicalTimeline]);

  const compressedLiveHistory = useMemo(() => {
    return compressHistoryData(liveCumulativeData, liveCumulativeTimeline, matchViewerCount);
  }, [liveCumulativeData, liveCumulativeTimeline]);

  const activeChartSource = useMemo(() => {
    if (isLiveTabSelected) {
      return liveTimeframe === 'cumulative' ? compressedLiveHistory : stableData;
    }
    return compressedHistory;
  }, [isLiveTabSelected, liveTimeframe, compressedLiveHistory, stableData, compressedHistory]);

  const activeSegments = useMemo(() => {
    if (isLiveTabSelected) {
      return liveTimeframe === 'cumulative' ? liveCumulativeSegments : segments;
    }
    return segments;
  }, [isLiveTabSelected, liveTimeframe, liveCumulativeSegments, segments]);

  useEffect(() => {
    if (activeChartSource.length > 0) {
      const currentMax = Math.max(...activeChartSource.map((d: any) => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax + 5);

      const currentMaxViewer = Math.max(...activeChartSource.map((d: any) => d.viewerCount || 0));
      if (currentMaxViewer > 0) {
        setMaxViewerY(Math.ceil(currentMaxViewer * 1.15));
      }
    }
  }, [activeChartSource, maxY]);

  const formatTime = (ts: any) => {
    if (!ts) return "";
    let d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  const chartDisplayData = useMemo(() => {
    const isFixedSlots = isLiveTabSelected && liveTimeframe === 'realtime';
    const totalSlots = isFixedSlots ? CONFIG.DISPLAY_POINTS : activeChartSource.length;
    const result = new Array(totalSlots);

    for (let i = 0; i < totalSlots; i++) {
      if (i < activeChartSource.length) {
        result[i] = { ...activeChartSource[i], slotIndex: i, hasData: true };
      } else {
        result[i] = { timestamp: null, value: null, slotIndex: i, hasData: false };
      }
    }
    return result;
  }, [activeChartSource, isLiveTabSelected, liveTimeframe]);

  const rebangIndexes = useMemo(() => {
    const historyList = isLiveTabSelected
      ? (liveTimeframe === 'cumulative' ? compressedLiveHistory : [])
      : compressedHistory;
    if (historyList.length === 0) return [];
    const indexes: number[] = [];
    for (let i = 1; i < historyList.length; i++) {
      const prev = historyList[i - 1];
      const curr = historyList[i];
      const timeDiff = curr.timestamp - prev.timestamp;

      if (timeDiff > 600000 || (curr.offsetMs !== undefined && prev.offsetMs !== undefined && curr.offsetMs < prev.offsetMs)) {
        indexes.push(i);
      }
    }
    return indexes;
  }, [compressedHistory, compressedLiveHistory, isLiveTabSelected, liveTimeframe]);

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

  const viewerMetric = useMemo(() => {
    if (hoveredData.viewers !== null && hoveredData.viewers !== undefined) {
      return { label: "해당 시점 시청자", value: hoveredData.viewers };
    }

    if (isLiveTabSelected) {
      if (liveTimeframe === 'cumulative') {
        const maxViewer = compressedLiveHistory.length > 0
          ? Math.max(...compressedLiveHistory.map((d: any) => d.viewerCount || 0))
          : (streamerInfo?.concurrentUserCount || 0);
        return { label: "현재 방송 최고 시청자", value: maxViewer };
      }

      const lastViewer = stableData.length > 0 && stableData[stableData.length - 1].viewerCount !== undefined
        ? stableData[stableData.length - 1].viewerCount
        : (streamerInfo?.concurrentUserCount || 0);
      return { label: "현재 실시간 시청자", value: lastViewer };
    }

    const displayLabel = currentSessionInfo ? currentSessionInfo.label : "과거 방송";
    const maxViewer = compressedHistory.length > 0
      ? Math.max(...compressedHistory.map((d: any) => d.viewerCount || 0))
      : (currentSessionInfo?.viewers || 0);

    return { label: `${displayLabel} 최고 시청자`, value: maxViewer };
  }, [isLiveTabSelected, liveTimeframe, stableData, compressedHistory, compressedLiveHistory, hoveredData, streamerInfo?.concurrentUserCount, currentSessionInfo]);

  const metric = useMemo(() => {
    if (hoveredData.value !== null) return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };

    const displayLabel = currentSessionInfo ? currentSessionInfo.label : "분석 데이터";

    if (isLiveTabSelected) {
      if (liveTimeframe === 'cumulative') {
        const maxVal = compressedLiveHistory.length > 0
          ? Math.max(...compressedLiveHistory.map((d: any) => d.value || 0))
          : 0;
        return { label: "현재 방송 최고 화력", value: maxVal };
      }

      const lastValue = stableData.length > 0 ? stableData[stableData.length - 1].value : 0;
      return { label: "현재 실시간 화력", value: lastValue };
    }

    const maxVal = compressedHistory.length > 0 ? Math.max(...compressedHistory.map((d: any) => d.value || 0)) : 0;

    return {
      label: `${displayLabel} 최고 화력`,
      value: maxVal
    };
  }, [isLiveTabSelected, liveTimeframe, stableData, compressedHistory, compressedLiveHistory, hoveredData, currentSessionInfo]);

  const displayTitle = isLiveTabSelected ? streamerInfo?.liveTitle : currentSessionInfo?.liveTitle;
  const displayCategory = isLiveTabSelected ? streamerInfo?.categoryName : currentSessionInfo?.categoryName;
  const displayViewers = isLiveTabSelected ? streamerInfo?.concurrentUserCount : currentSessionInfo?.viewers;

  if (!streamId) return <div className="p-10 text-center text-gray-100">잘못된 접근입니다.</div>;

  const isChartLoading = isLiveTabSelected
    ? (liveTimeframe === 'realtime' ? isLoading : isLiveCumulativeLoading)
    : false;
  const isHistoryEmpty = isLiveTabSelected
    ? (liveTimeframe === 'cumulative' && !isLiveCumulativeLoading && compressedLiveHistory.length === 0)
    : historicalData.length === 0;

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

      <AnalysisChart
        chartData={chartDisplayData}
        metric={metric}
        viewerMetric={viewerMetric}
        maxY={maxY}
        maxViewerY={maxViewerY}
        isLoading={isChartLoading}
        isGathering={isGathering}
        error={isLiveTabSelected ? error : null}
        selectedTab={isLiveTabSelected ? "realtime" : selectedTab}
        historyEmpty={isHistoryEmpty}
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setHoveredData({ value: null, viewers: null, time: null })}
        formatTime={formatTime}
        rebangIndexes={rebangIndexes}
        segments={activeSegments}
        highlights={highlights}
        showTimeframeToggle={isLiveTabSelected}
        timeframe={liveTimeframe}
        onTimeframeChange={setLiveTimeframe}
      />

      <SessionSummaryGrid
        isLive={isLiveTabSelected}
        startedAt={isLiveTabSelected ? availableSessions[0]?.startedAt : currentSessionInfo?.startedAt}
        endedAt={isLiveTabSelected ? null : currentSessionInfo?.endedAt}
        currentViewers={isLiveTabSelected ? (streamerInfo?.concurrentUserCount || 0) : 0}
        timeline={isLiveTabSelected ? (liveCumulativeTimeline.length > 0 ? liveCumulativeTimeline : (analysisData?.timeline || [])) : historicalTimeline}
        dataPoints={isLiveTabSelected ? (liveCumulativeData.length > 0 ? liveCumulativeData : stableData) : historicalData}
        summaryAvg={currentSessionInfo?.averageViewerCount}
        summaryPeak={currentSessionInfo?.peakViewers}
        subscriberChatRate={currentSessionInfo?.subscriberChatRatio ?? null}
      />

      <HighlightSection
        highlights={highlights}
        selectedTab={isLiveTabSelected ? "realtime" : selectedTab}
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
