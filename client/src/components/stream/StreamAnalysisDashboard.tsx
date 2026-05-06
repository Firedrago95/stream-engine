import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AnalysisTabs } from './dashboard/AnalysisTabs';
import { AnalysisChart } from './dashboard/AnalysisChart';
import { HighlightSection } from './dashboard/HighlightSection';
import { DashboardHeader } from './dashboard/DashboardHeader';
import { StreamProfileHeader } from './dashboard/StreamProfileHeader';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';
import { useHighlights } from '../../hooks/useHighlights';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const CONFIG = {
  POLLING_INTERVAL: 3000,
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

    if (diffDays === 0) return "오늘 방송";
    if (diffDays === 1) return "어제 방송";
    if (diffDays === 2) return "이틀 전 방송";
    return `${diffDays}일 전 방송`;
  } catch (e) {
    return "과거 방송";
  }
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  const [selectedTab, setSelectedTab] = useState<string>("realtime");

  const [availableSessions, setAvailableSessions] = useState<{ 
    sessionId: string; 
    label: string;
    liveTitle?: string;
    categoryName?: string;
    viewers?: number;
  }[]>([
    { sessionId: "realtime", label: "⚡ 실시간 분석" }
  ]);

  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

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
    return points.slice(-CONFIG.DISPLAY_POINTS);
  }, [analysisData]);

  const { highlights } = useHighlights(streamId || "", selectedTab, CONFIG.POLLING_INTERVAL);

  const [streamerInfo, setStreamerInfo] = useState<any>(null);
  const [isLive, setIsLive] = useState(false);
  const [historicalData, setHistoricalData] = useState<any[]>([]);
  const [maxY, setMaxY] = useState(10);
  const [hoveredData, setHoveredData] = useState<{ value: number | null; time: string | null }>({
    value: null, time: null,
  });

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
    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/available-sessions?limit=10`)
    .then(res => res.ok ? res.json() : [])
    .then((sessions: any[]) => {
      const formattedSessions = sessions.map(s => ({
        sessionId: s.sessionId,
        label: getRelativeLabel(s.startedAt),
        liveTitle: s.liveTitle || "[과거 방송] " + getRelativeLabel(s.startedAt),
        categoryName: s.categoryName || "종합 게임",
        viewers: 0
      }));
      setAvailableSessions([
        { sessionId: "realtime", label: "⚡ 실시간 분석" },
        ...formattedSessions
      ]);
    })
    .catch(err => console.error("세션 목록 로드 실패", err));
  }, [streamId]);

  useEffect(() => {
    if (selectedTab === "realtime" || !streamId) {
      setHistoricalData([]);
      return;
    }

    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/history?sessionId=${selectedTab}`)
    .then(res => res.ok ? res.json() : { dataPoints: [] })
    .then(data => {
      const sortedHistory = (data.dataPoints || []).sort((a: any, b: any) => a.timestamp - b.timestamp);
      setHistoricalData(sortedHistory);
    })
    .catch(err => {
      console.error("과거 데이터를 불러오지 못했습니다.", err);
      setHistoricalData([]);
    });
  }, [selectedTab, streamId]);

  // 프론트엔드 동적 압축(Dynamic Aggregation) 로직
  const compressedHistory = useMemo(() => {
    if (historicalData.length === 0) return [];
    const totalMinutes = historicalData.length;

    // 방송 길이에 따라 묶음(Interval) 동적 조절
    let interval = 1;
    if (totalMinutes > 360) interval = 5; // 6시간 이상: 5분 압축
    else if (totalMinutes > 180) interval = 3; // 3시간 이상: 3분 압축

    // 압축이 필요 없으면 원본 리턴
    if (interval === 1) return historicalData;

    const intervalMs = interval * 60 * 1000;
    const grouped: Record<number, any> = {};

    historicalData.forEach((p: any) => {
      // 시간 버킷(구간) 계산
      const bucket = Math.floor(p.timestamp / intervalMs) * intervalMs;
      if (!grouped[bucket]) {
        grouped[bucket] = { ...p, timestamp: bucket };
      } else {
        // 해당 구간의 최고 화력(MAX)만 추출해서 덮어씀
        grouped[bucket].value = Math.max(grouped[bucket].value || 0, p.value || 0);
        // 상태값 보존
        if (p.status === 'PEAK') grouped[bucket].status = 'PEAK';
        // 오프셋 보존 (구간 중 가장 빠른 시간)
        if (p.offsetMs !== undefined && (grouped[bucket].offsetMs === undefined || p.offsetMs < grouped[bucket].offsetMs)) {
          grouped[bucket].offsetMs = p.offsetMs;
        }
      }
    });

    return Object.values(grouped).sort((a: any, b: any) => a.timestamp - b.timestamp);
  }, [historicalData]);

  // Y축 최대값 계산 (압축된 데이터 기반)
  useEffect(() => {
    const targetData = selectedTab === "realtime" ? stableData : compressedHistory;
    if (targetData.length > 0) {
      const currentMax = Math.max(...targetData.map((d: any) => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax + 5);
    }
  }, [stableData, compressedHistory, selectedTab, maxY]);

  const formatTime = (ts: any) => {
    if (!ts) return "";
    let d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  // 차트 표시 데이터 조립 (압축된 데이터 기반)
  const chartDisplayData = useMemo(() => {
    const currentSource = selectedTab === "realtime" ? stableData : compressedHistory;
    const totalSlots = selectedTab === "realtime" ? CONFIG.DISPLAY_POINTS : currentSource.length;
    const result = new Array(totalSlots);

    for (let i = 0; i < totalSlots; i++) {
      if (i < currentSource.length) {
        result[i] = { ...currentSource[i], slotIndex: i, hasData: true };
      } else {
        result[i] = { timestamp: null, value: null, slotIndex: i, hasData: false };
      }
    }
    return result;
  }, [stableData, compressedHistory, selectedTab]);

  // 리방 인덱스 선 긋기 (압축된 데이터 기반으로 갭 허용치 10분으로 증가)
  const rebangIndexes = useMemo(() => {
    if (selectedTab === "realtime" || compressedHistory.length === 0) return [];
    const indexes: number[] = [];
    for (let i = 1; i < compressedHistory.length; i++) {
      const prev = compressedHistory[i - 1];
      const curr = compressedHistory[i];
      const timeDiff = curr.timestamp - prev.timestamp;

      // 5분 단위로 압축되었을 수 있으므로 10분(600,000ms) 이상 차이 날 때 리방으로 간주
      if (timeDiff > 600000 || (curr.offsetMs !== undefined && prev.offsetMs !== undefined && curr.offsetMs < prev.offsetMs)) {
        indexes.push(i);
      }
    }
    return indexes;
  }, [compressedHistory, selectedTab]);

  const handleMouseMove = (state: any) => {
    if (state?.activePayload?.[0]?.payload?.hasData) {
      const p = state.activePayload[0].payload;
      setHoveredData({ value: p.value, time: formatTime(p.timestamp) });
    }
  };

  const metric = useMemo(() => {
    if (hoveredData.value !== null) return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };

    // 현재 선택된 세션의 정보를 찾음
    const currentSession = availableSessions.find(s => s.sessionId === selectedTab);
    const displayLabel = currentSession ? currentSession.label : "분석 데이터";

    if (selectedTab === "realtime") {
      const lastValue = stableData.length > 0 ? stableData[stableData.length - 1].value : 0;
      return { label: "현재 실시간 화력", value: lastValue };
    }

    const maxVal = compressedHistory.length > 0 ? Math.max(...compressedHistory.map((d: any) => d.value || 0)) : 0;

    return {
      label: (
          <div className="flex flex-col gap-1.5">
            <span className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Broadcast History</span>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-black text-[#00FFA3] tracking-tighter">
            {displayLabel}
          </span>
              <span className="text-lg font-bold text-gray-400">최고 화력</span>
            </div>
          </div>
      ),
      value: maxVal
    };
  }, [selectedTab, stableData, compressedHistory, hoveredData, availableSessions]);

  const currentSessionInfo = availableSessions.find(s => s.sessionId === selectedTab);

  const displayTitle = selectedTab === "realtime" ? streamerInfo?.liveTitle : currentSessionInfo?.liveTitle;
  const displayCategory = selectedTab === "realtime" ? streamerInfo?.categoryName : currentSessionInfo?.categoryName;
  const displayViewers = selectedTab === "realtime" ? streamerInfo?.concurrentUserCount : currentSessionInfo?.viewers;
  const displayIsLive = selectedTab === "realtime" ? isLive : false;

  if (!streamId) return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full pb-20 bg-[#060606] min-h-screen text-white px-4 sm:px-8">
        <DashboardHeader onBack={() => navigate(-1)} />
        <StreamProfileHeader
            streamId={streamId}
            streamerName={streamerInfo?.streamerName}
            profileImageUrl={streamerInfo?.profileImageUrl}
            isLive={displayIsLive}             
            status={streamerInfo?.status}
            viewers={displayViewers}           
            liveTitle={displayTitle}           
            categoryName={displayCategory}     
        />

        <AnalysisTabs
            availableSessions={availableSessions}
            selected={selectedTab}
            onSelect={(tab) => {
              setSelectedTab(tab);
              setHoveredData({ value: null, time: null });
            }}
        />

        <AnalysisChart
            chartData={chartDisplayData}
            metric={metric}
            maxY={maxY}
            isLoading={isLoading}
            isGathering={isGathering}
            error={error}
            selectedTab={selectedTab}
            historyEmpty={selectedTab !== "realtime" && historicalData.length === 0}
            onMouseMove={handleMouseMove}
            onMouseLeave={() => setHoveredData({value:null, time:null})}
            formatTime={formatTime}
            rebangIndexes={rebangIndexes}
        />

        <HighlightSection
            highlights={highlights}
            selectedTab={selectedTab}
        />

        <footer className="mt-24 pt-12 border-t border-gray-800/60 text-center">
          <div className="mb-6">
            <span className="text-[#00FFA3] font-black text-xl italic tracking-tighter uppercase">Cheese Pick</span>
          </div>
          <a
              href="https://forms.gle/hUkZBr9KCTDyTXLW9"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 text-gray-400 hover:text-[#00FFA3] transition-all text-sm font-bold bg-[#1a1a1c] px-8 py-3.5 rounded-full border border-gray-800 hover:border-[#00FFA3]/50 shadow-xl group"
          >
            💡 치즈픽 하이라이트 엔진 피드백 보내기
            <svg className="w-4 h-4 transform group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </a>
          <p className="mt-8 text-[11px] text-gray-600 font-medium tracking-widest uppercase">
            © 2026 CheesePick. Advanced Stream Analytics Pipeline.
          </p>
        </footer>
      </div>
  );
};
