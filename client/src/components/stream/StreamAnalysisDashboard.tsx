import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';
import { useHighlights } from '../../hooks/useHighlights';

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

  const availableDates = ["realtime", "2026-03-05", "2026-03-04"];

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
    if (selectedTab !== "realtime") return;

    const incomingPoints = analysisData?.dataPoints || [];
    if (incomingPoints.length === 0) return;

    setStableData(prev => {
      const lastTimestamp = prev.length > 0 ? prev[prev.length - 1].timestamp : 0;
      const trulyNew = incomingPoints.filter((p: any) => p.timestamp > lastTimestamp);
      if (trulyNew.length === 0) return prev;
      const newData = [...prev, ...trulyNew];
      return newData.slice(-CONFIG.DISPLAY_POINTS);
    });
  }, [analysisData, selectedTab]);

  useEffect(() => {
    if (selectedTab !== "realtime") {
      setHistoricalData([]);
    }
  }, [selectedTab]);

  useEffect(() => {
    const targetData = selectedTab === "realtime" ? stableData : historicalData;
    if (targetData.length > 0) {
      const currentMax = Math.max(...targetData.map(d => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax + 5);
    }
  }, [stableData, historicalData, selectedTab, maxY]);

  // 💡 [버그 수정] CodeRabbit 리뷰 반영: Epoch Seconds 방어 로직 추가
  const formatTime = (ts: any) => {
    if (!ts) return "";
    let d;
    // 숫자로 들어왔을 경우 (Spring Boot Instant 직렬화 이슈 대응)
    if (typeof ts === 'number') {
      // 10자리 숫자(초 단위)면 밀리초로 변환 (10000000000 미만인지 체크)
      d = ts < 10000000000 ? new Date(ts * 1000) : new Date(ts);
    } else {
      // 문자열(ISO 8601 등)로 들어왔을 경우
      d = new Date(ts);
    }
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
  };

  const chartDisplayData = useMemo(() => {
    const currentSource = selectedTab === "realtime" ? stableData : historicalData;
    const totalSlots = CONFIG.DISPLAY_POINTS;
    const result = new Array(totalSlots);

    for (let i = 0; i < totalSlots; i++) {
      const dataIndex = i - (totalSlots - currentSource.length);
      if (dataIndex >= 0) {
        result[i] = { ...currentSource[dataIndex], slotIndex: i, hasData: true };
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
    if (hoveredData.value !== null) {
      return { label: `시점 화력 (${hoveredData.time})`, value: hoveredData.value };
    }
    if (selectedTab === "realtime") {
      const val = stableData.length > 0 ? stableData[stableData.length - 1].value : 0;
      return { label: "현재 실시간 화력", value: val };
    }
    return { label: "과거 분석 데이터를 확인하세요", value: "-" };
  }, [selectedTab, stableData, hoveredData]);

  if (!streamId) return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full max-w-7xl mx-auto px-4 pb-20">
        <div className="py-8 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-2 bg-[#1a1a1c] border border-gray-700 rounded-lg text-gray-300 hover:text-[#00FFA3]">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
          </button>
          <h2 className="text-xl font-bold text-white tracking-tight">분석 대시보드</h2>
        </div>

        <div className="mb-8 p-6 bg-[#1a1a1c] border border-gray-800 rounded-2xl flex items-center gap-6">
          <div className="w-20 h-20 rounded-full border-2 border-[#00FFA3]/20 overflow-hidden bg-gray-900 shadow-xl">
            <img src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${streamId}`} alt="profile" />
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-white font-bold text-lg">스트리머 분석 정보</span>
              <span className={`px-2 py-0.5 text-[10px] ${isLive ? 'bg-red-600 animate-pulse' : 'bg-gray-800'} text-white rounded-sm font-black`}>
              {isLive ? 'LIVE' : 'OFFLINE'}
            </span>
              <button onClick={() => setIsLive(!isLive)} className="ml-2 text-[10px] text-gray-600 border border-gray-800 px-1 py-0.5 rounded">상태 전환</button>
            </div>
            <h1 className="text-2xl font-black text-white">치즈슬라이스 실시간 대시보드</h1>
          </div>
        </div>

        <div className="mb-8 flex gap-2">
          {availableDates.map((tab) => (
              <button
                  key={tab}
                  onClick={() => {
                    setSelectedTab(tab);
                    setHoveredData({ value: null, time: null });
                  }}
                  className={`px-6 py-2.5 rounded-full text-sm font-bold transition-all border ${
                      selectedTab === tab ? 'bg-[#00FFA3] border-[#00FFA3] text-gray-900 shadow-[0_0_15px_rgba(0,255,163,0.3)]' : 'bg-gray-800 border-gray-700 text-gray-400 hover:text-white'
                  }`}>
                {tab === "realtime" ? "⚡ 실시간 분석" : tab.slice(5)}
              </button>
          ))}
        </div>

        <div className="p-8 bg-[#0c0d0f] border border-gray-800 rounded-3xl relative overflow-hidden min-h-[500px]">
          {/* 💡 [버그 수정] CodeRabbit 리뷰 반영: 무한 스피너 방지 및 미구현 안내 */}
          {selectedTab === "realtime" && (isLoading || isGathering || stableData.length === 0) && !error && (
              <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
                <div className="w-12 h-12 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
                <p className="text-[#00FFA3] font-black tracking-widest">데이터 로딩 중...</p>
              </div>
          )}

          {selectedTab !== "realtime" && historicalData.length === 0 && (
              <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
                <span className="text-5xl mb-4">🚧</span>
                <p className="text-gray-300 font-bold tracking-widest">과거 데이터 조회 API 연결 준비 중입니다.</p>
              </div>
          )}

          <div className="flex justify-between items-start mb-10">
            <h3 className="text-lg font-bold text-gray-400 uppercase tracking-widest italic">채팅 화력 추이</h3>
            <div className="text-right">
              <span className="text-[10px] text-gray-500 font-bold block mb-1 uppercase tracking-tighter">{metric.label}</span>
              <span className="text-4xl font-black text-[#00FFA3]">
              {metric.value} <span className="text-xs text-gray-600 ml-2 italic">건/초</span>
            </span>
            </div>
          </div>

          <div className="h-[350px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                  data={chartDisplayData}
                  margin={{ top: 0, right: 0, left: -20, bottom: 0 }}
                  onMouseMove={handleMouseMove}
                  onMouseLeave={() => setHoveredData({value:null, time:null})}
              >
                <defs>
                  <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0.3}/>
                    <stop offset="95%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />
                <XAxis
                    dataKey="slotIndex"
                    tickFormatter={(idx) => chartDisplayData[idx]?.timestamp ? formatTime(chartDisplayData[idx].timestamp) : ""}
                    stroke="#475569"
                    fontSize={11}
                    tickMargin={15}
                    axisLine={false}
                    tickLine={false}
                />
                <YAxis stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />

                <Tooltip
                    cursor={{ stroke: CONFIG.CHART_COLOR, strokeWidth: 1, strokeDasharray: '4 4' }}
                    contentStyle={{ backgroundColor: "#1a1a1c", border: '1px solid #334155', borderRadius: '12px' }}
                    labelFormatter={(idx) => chartDisplayData[idx as number]?.timestamp ? `시점: ${formatTime(chartDisplayData[idx as number].timestamp)}` : ''}
                    formatter={(val) => [`${val} 건/초`, '화력']}
                />
                <Area
                    type="monotone"
                    dataKey="value"
                    stroke={CONFIG.CHART_COLOR}
                    strokeWidth={4}
                    fill="url(#colorValue)"
                    isAnimationActive={false}
                    connectNulls={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="mt-12">
          <div className="flex items-center gap-2 mb-6 px-2">
            <span className="text-2xl">🔥</span>
            <h3 className="text-xl font-bold text-white italic uppercase tracking-tighter">주요 하이라이트</h3>
            <span className="ml-2 px-2 py-0.5 bg-gray-800 text-[#00FFA3] text-xs font-bold rounded border border-gray-700">{highlights.length}</span>
          </div>

          <div className="grid gap-3">
            {highlights.length === 0 ? (
                <div className="p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-600 font-bold italic text-sm">아직 감지된 하이라이트가 없습니다.</div>
            ) : (
                [...highlights].reverse().map((hl) => (
                    <div key={hl.id} className={`flex items-center p-6 bg-[#1a1a1c] border ${hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800'} rounded-2xl`}>
                      <div className="flex-1">
                        <span className="text-[10px] text-gray-500 font-black uppercase mb-1 block">시간대</span>
                        <div className="flex items-center gap-3">
                          <span className="text-white font-mono text-xl font-black">{formatTime(hl.startTime)}</span>
                          <span className="text-white font-black text-xl px-1">~</span>
                          {hl.status === 'ONGOING' ? (
                              <span className="text-red-500 animate-pulse text-sm font-black italic">진행 중 🔴</span>
                          ) : (
                              <span className="text-white font-mono text-xl font-black">{formatTime(hl.endTime)}</span>
                          )}
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-[10px] text-gray-500 font-black uppercase mb-1 block">최고 화력</span>
                        <span className="text-[#00FFA3] font-black text-2xl">{hl.peakFirepower}<span className="text-xs text-gray-700 ml-1">msg/s</span></span>
                      </div>
                    </div>
                ))
            )}
          </div>
        </div>
      </div>
  );
};
