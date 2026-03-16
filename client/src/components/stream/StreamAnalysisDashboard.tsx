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

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  const [selectedTab, setSelectedTab] = useState<string>("realtime");

  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

  const stableData = useMemo(() => analysisData || [], [analysisData]);

  const { highlights } = useHighlights(streamId || "", selectedTab, CONFIG.POLLING_INTERVAL);

  const [streamerInfo, setStreamerInfo] = useState<any>(null);
  const [isLive, setIsLive] = useState(false);
  const [availableDates, setAvailableDates] = useState<string[]>(["realtime"]);
  const [historicalData, setHistoricalData] = useState<any[]>([]);
  const [maxY, setMaxY] = useState(10);
  const [hoveredData, setHoveredData] = useState<{ value: number | null; time: string | null }>({
    value: null, time: null,
  });

  // 1. 스트리머 정보 및 라이브 상태 로드
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

  // 2. 가용 날짜 목록 로드
  useEffect(() => {
    if (!streamId) return;
    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/available-dates?limit=10`)
    .then(res => res.ok ? res.json() : [])
    .then((dates: string[]) => {
      setAvailableDates(["realtime", ...dates]);
    })
    .catch(err => console.error("날짜 목록을 불러오지 못했습니다.", err));
  }, [streamId]);

  // 3. 과거 탭 선택 시 데이터 로드
  useEffect(() => {
    if (selectedTab === "realtime" || !streamId) {
      setHistoricalData([]);
      return;
    }

    fetch(`${API_BASE_URL}/api/v1/analysis/streams/${streamId}/history?date=${selectedTab}`)
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

  // 4. 차트 Y축 최대값 동적 계산
  useEffect(() => {
    const targetData = selectedTab === "realtime" ? stableData : historicalData;
    if (targetData.length > 0) {
      const currentMax = Math.max(...targetData.map((d: any) => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax + 5);
    }
  }, [stableData, historicalData, selectedTab, maxY]);

  const formatTime = (ts: any) => {
    if (!ts) return "";
    let d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  const chartDisplayData = useMemo(() => {
    const currentSource = selectedTab === "realtime" ? stableData : historicalData;
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
  }, [stableData, historicalData, selectedTab]);

  const rebangIndexes = useMemo(() => {
    if (selectedTab === "realtime" || historicalData.length === 0) return [];
    const indexes: number[] = [];
    for (let i = 1; i < historicalData.length; i++) {
      const prev = historicalData[i - 1];
      const curr = historicalData[i];
      const timeDiff = curr.timestamp - prev.timestamp;

      if (timeDiff > 360000 || (curr.offsetMs !== undefined && prev.offsetMs !== undefined && curr.offsetMs < prev.offsetMs)) {
        indexes.push(i);
      }
    }
    return indexes;
  }, [historicalData, selectedTab]);

  const handleMouseMove = (state: any) => {
    if (state?.activePayload?.[0]?.payload?.hasData) {
      const p = state.activePayload[0].payload;
      setHoveredData({ value: p.value, time: formatTime(p.timestamp) });
    }
  };

  const metric = useMemo(() => {
    if (hoveredData.value !== null) return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };
    if (selectedTab === "realtime") {
      const lastValue = stableData.length > 0 ? stableData[stableData.length - 1].value : 0;
      return { label: "현재 실시간 화력", value: lastValue };
    }
    return { label: `${selectedTab} 분석 리포트`, value: historicalData.length > 0 ? "조회 완료" : "-" };
  }, [selectedTab, stableData, historicalData, hoveredData]);

  if (!streamId) return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full pb-20 bg-[#060606] min-h-screen text-white px-4 sm:px-8">
        {/* ✨ 주석 제거됨: 헤더 섹션 활성화 */}
        <DashboardHeader onBack={() => navigate(-1)} />
        <StreamProfileHeader
            streamId={streamId}
            streamerName={streamerInfo?.streamerName}
            profileImageUrl={streamerInfo?.profileImageUrl}
            isLive={isLive}
            status={streamerInfo?.status}
            viewers={streamerInfo?.concurrentUserCount}
            liveTitle={streamerInfo?.liveTitle}
            categoryName={streamerInfo?.categoryName}
        />

        <AnalysisTabs
            availableDates={availableDates}
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

        <HighlightSection highlights={highlights}/>

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
