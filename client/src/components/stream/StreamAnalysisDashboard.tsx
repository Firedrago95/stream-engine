import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';
import { useHighlights } from '../../hooks/useHighlights';

import { DashboardHeader } from './dashboard/DashboardHeader';
import { StreamProfileHeader } from './dashboard/StreamProfileHeader';
import { AnalysisTabs } from './dashboard/AnalysisTabs';
import { AnalysisChart } from './dashboard/AnalysisChart';
import { HighlightSection } from './dashboard/HighlightSection';

const CONFIG = {
  POLLING_INTERVAL: 5000,
  DISPLAY_POINTS: 20,
  CHART_COLOR: "#00FFA3",
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  const [isLive, setIsLive] = useState(true);
  const [selectedTab, setSelectedTab] = useState("realtime");
  const [availableDates, setAvailableDates] = useState<string[]>(["realtime"]);
  const [streamerInfo, setStreamerInfo] = useState<{
    streamerName: string;
    profileImageUrl: string;
    liveTitle: string;
    status: string;
    concurrentUserCount?: number;
  } | null>(null);

  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

  const { highlights } = useHighlights(streamId || "", selectedTab, CONFIG.POLLING_INTERVAL);

  const [stableData, setStableData] = useState<any[]>([]);
  const [historicalData, setHistoricalData] = useState<any[]>([]);
  const [maxY, setMaxY] = useState(10);
  const [hoveredData, setHoveredData] = useState<{ value: number | null; time: string | null }>({
    value: null, time: null,
  });

  useEffect(() => {
    if (!streamId) return;
    fetch(`/api/v1/streams/${streamId}`)
    .then(res => res.ok ? res.json() : null)
    .then(data => {
      if (data) {
        setStreamerInfo(data);
        setIsLive(data.status != 'OFFLINE');
      }
    })
    .catch(err => console.error("스트리머 정보를 불러오는데 실패했습니다.",err));
  }, [streamId]);

  useEffect(() => {
    if (!streamId) return;
    fetch(`/api/v1/analysis/streams/${streamId}/available-dates?limit=10`)
    .then(res => res.ok ? res.json() : [])
    .then((dates: string[]) => {
      setAvailableDates(["realtime", ...dates]);
    })
    .catch(err => console.error("날짜 목록을 불러오지 못했습니다.", err));
  }, [streamId]);

  useEffect(() => {
    if (selectedTab !== "realtime") return;
    const incomingPoints = analysisData?.dataPoints || [];
    if (incomingPoints.length === 0) return;

    setStableData(prev => {
      const lastTimestamp = prev.length > 0 ? prev[prev.length - 1].timestamp : 0;
      const trulyNew = incomingPoints.filter((p: any) => p.timestamp > lastTimestamp);
      if (trulyNew.length === 0) return prev;
      const combined = [...prev, ...trulyNew].sort((a, b) => a.timestamp - b.timestamp);
      return combined.slice(-CONFIG.DISPLAY_POINTS);
    });
  }, [analysisData, selectedTab]);

  useEffect(() => {
    if (selectedTab === "realtime" || !streamId) {
      setHistoricalData([]);
      return;
    }

    fetch(`/api/v1/analysis/streams/${streamId}/history?date=${selectedTab}`)
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

  useEffect(() => {
    const targetData = selectedTab === "realtime" ? stableData : historicalData;
    if (targetData.length > 0) {
      const currentMax = Math.max(...targetData.map(d => d.value || 0));
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

  const handleMouseMove = (state: any) => {
    if (state?.activePayload?.[0]?.payload?.hasData) {
      const p = state.activePayload[0].payload;
      setHoveredData({ value: p.value, time: formatTime(p.timestamp) });
    }
  };

  const metric = useMemo(() => {
    if (hoveredData.value !== null) return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };
    if (selectedTab === "realtime") return { label: "현재 실시간 화력", value: stableData.length > 0 ? stableData[stableData.length - 1].value : 0 };
    return { label: "과거 분석 데이터를 확인하세요", value: "-" };
  }, [selectedTab, stableData, hoveredData]);

  if (!streamId) return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full pb-20">
        <DashboardHeader onBack={() => navigate(-1)} />
        <StreamProfileHeader
            streamId={streamId}
            streamerName={streamerInfo?.streamerName}
            profileImageUrl={streamerInfo?.profileImageUrl}
            isLive={isLive}
            status={streamerInfo?.status}
            viewers={streamerInfo?.concurrentUserCount}
        />
        <AnalysisTabs availableDates={availableDates} selected={selectedTab} onSelect={(tab) => { setSelectedTab(tab); setHoveredData({ value: null, time: null }); }} />
        <AnalysisChart
            chartData={chartDisplayData} metric={metric} maxY={maxY} isLoading={isLoading} isGathering={isGathering}
            error={error} selectedTab={selectedTab} historyEmpty={historicalData.length === 0}
            onMouseMove={handleMouseMove} onMouseLeave={() => setHoveredData({value:null, time:null})} formatTime={formatTime}
        />
        <HighlightSection highlights={highlights} formatTime={formatTime} />
      </div>
  );
};
