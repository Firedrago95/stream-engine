import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

/**
 * 💡 대시보드 환경 설정
 */
const CONFIG = {
  POLLING_INTERVAL: 5000,      // 데이터 수집 주기 (5초)
  DISPLAY_POINTS: 20,          // X축 슬롯 개수 (20개 고정)
  CHART_COLOR: "#00FFA3",      // 치지직 연두색
  BG_DARK: "#0c0d0f",          // 깊은 블랙-그레이 hex
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  // useStreamAnalysis 훅 호출
  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

  const [stableData, setStableData] = useState<any[]>([]);
  const [maxY, setMaxY] = useState(10);

  // 데이터 업데이트 로직
  useEffect(() => {
    const incomingPoints = analysisData?.dataPoints || [];
    if (incomingPoints.length === 0) return;

    setStableData(prev => {
      const lastTimestamp = prev.length > 0 ? prev[prev.length - 1].timestamp : 0;
      const trulyNew = incomingPoints.filter((p: any) => p.timestamp > lastTimestamp);

      if (trulyNew.length === 0) return prev;

      const newData = [...prev, ...trulyNew];
      return newData.slice(-CONFIG.DISPLAY_POINTS);
    });
  }, [analysisData]);

  // Y축 최대값 고정 (출렁임 방지)
  useEffect(() => {
    if (stableData.length > 0) {
      const currentMax = Math.max(...stableData.map(d => d.value || 0));
      if (currentMax > maxY) setMaxY(currentMax);
    }
  }, [stableData, maxY]);

  // 고정 슬롯 데이터 생성
  const chartDisplayData = useMemo(() => {
    const totalSlots = CONFIG.DISPLAY_POINTS;
    const result = new Array(totalSlots);

    for (let i = 0; i < totalSlots; i++) {
      if (i < stableData.length) {
        result[i] = { ...stableData[i], slotIndex: i, hasData: true };
      } else {
        result[i] = { timestamp: null, value: null, slotIndex: i, hasData: false };
      }
    }
    return result;
  }, [stableData]);

  /**
   * 💡 [수정] X축 포맷터: 분:초 (mm:ss) 만 표시
   */
  const formatXAxis = (_: any, index: number) => {
    const item = chartDisplayData[index];
    if (!item || !item.hasData || !item.timestamp) return "";
    const date = new Date(item.timestamp);
    const mins = date.getMinutes().toString().padStart(2, '0');
    const secs = date.getSeconds().toString().padStart(2, '0');
    return `${mins}:${secs}`;
  };

  if (!streamId) return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full max-w-7xl mx-auto px-4">
        <div className="mb-8 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-slate-500 transition-colors">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
          </button>
          <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100">실시간 화력 분석</h2>
        </div>

        <div className="p-6 bg-white dark:bg-[#0c0d0f] border border-slate-200 dark:border-slate-700 rounded-xl shadow-sm relative overflow-hidden">

          {/* 심플 로딩 UI */}
          {(isLoading || isGathering || stableData.length === 0) && !error && (
              <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/40 backdrop-blur-[1px]">
                <div className="flex flex-col items-center animate-pulse">
                  <div className="w-12 h-12 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
                  <p className="text-gray-100 text-lg font-bold tracking-widest">데이터 연결 중</p>
                </div>
              </div>
          )}

          {/* 에러 UI */}
          {error && (
              <div className="absolute inset-0 z-20 flex items-center justify-center bg-gray-900/80 backdrop-blur-sm">
                <div className="text-center p-6 bg-gray-800 border border-rose-500/30 rounded-xl">
                  <p className="text-rose-400 font-bold mb-2 text-lg">연결 에러 발생</p>
                  <p className="text-gray-300 text-sm">{error}</p>
                </div>
              </div>
          )}

          <h3 className="text-lg font-semibold text-slate-800 dark:text-slate-100 mb-8 px-1">실시간 채팅 화력</h3>

          <div className="h-[400px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartDisplayData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0.25}/>
                    <stop offset="95%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} opacity={0.5} />

                {/* 💡 [수정] X축: stroke를 더 밝은 흰색(#e2e8f0)으로 변경 */}
                <XAxis
                    dataKey="slotIndex"
                    tickFormatter={formatXAxis}
                    stroke="#e2e8f0"
                    fontSize={11}
                    tickMargin={12}
                    interval={0}
                    axisLine={false}
                    tickLine={false}
                />

                {/* 💡 [수정] Y축: stroke를 더 밝은 흰색(#e2e8f0)으로 변경 */}
                <YAxis
                    stroke="#e2e8f0"
                    fontSize={11}
                    tickMargin={10}
                    domain={[0, maxY]}
                    allowDecimals={false}
                    axisLine={false}
                    tickLine={false}
                />

                <Tooltip
                    labelFormatter={(idx) => {
                      const label = formatXAxis(0, idx as number);
                      return label ? `시간: ${label}` : '';
                    }}
                    contentStyle={{ backgroundColor: CONFIG.BG_DARK, border: '1px solid #334155', borderRadius: '8px', color: '#f8fafc' }}
                    itemStyle={{ color: CONFIG.CHART_COLOR }}
                />

                <Area
                    type="monotone"
                    dataKey="value"
                    stroke={CONFIG.CHART_COLOR}
                    strokeWidth={3}
                    fill="url(#colorValue)"
                    isAnimationActive={false}
                    connectNulls={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
  );
};
