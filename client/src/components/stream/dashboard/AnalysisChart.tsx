// src/components/stream/dashboard/AnalysisChart.tsx

import React from 'react';
import { AreaChart, Area, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import type { StreamSegment } from '../../../types/StreamSegment';
import type { HighlightResponse } from '../../../hooks/useHighlights';

interface Props {
  chartData: any[];
  metric: {
    label: React.ReactNode;
    value: string | number
  };
  viewerMetric?: {
    label: React.ReactNode;
    value: string | number
  };
  maxY: number;
  maxViewerY?: number;
  isLoading: boolean;
  isGathering: boolean;
  error: string | null;
  selectedTab: string;
  historyEmpty: boolean;
  onMouseMove: (state: any) => void;
  onMouseLeave: () => void;
  formatTime: (ts: any) => string;
  rebangIndexes?: number[];
  segments: StreamSegment[];
  highlights?: HighlightResponse[];
  showTimeframeToggle?: boolean;
  timeframe?: 'realtime' | 'cumulative';
  onTimeframeChange?: (tf: 'realtime' | 'cumulative') => void;
}

const formatOffset = (ms: number | undefined | null) => {
  if (ms === null || ms === undefined) return "--:--:--";
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  if (h > 0) return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

const formatShortTime = (ts: any) => {
  if (!ts) return "";
  const d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
  return d.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
};

const CustomTooltip = ({ active, payload, selectedTab, timeframe = 'realtime', formatTime, segments = [] }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    if (!data.hasData) return null;

    const isFixedRealtime = selectedTab === "realtime" && timeframe === "realtime";
    const tsMs = data.timestamp < 10000000000 ? data.timestamp * 1000 : data.timestamp;
    const activeSeg = isFixedRealtime ? null : segments.find((seg: any) => {
      const start = new Date(seg.startedAt).getTime();
      const end = seg.endedAt ? new Date(seg.endedAt).getTime() : Infinity;
      return tsMs >= start && tsMs < end;
    });

    return (
      <div className="bg-[#1a1a1c] border border-gray-700 p-3 rounded-xl shadow-2xl z-50 min-w-[200px]">
        <div className="flex items-center justify-between gap-4 mb-1.5">
          <div className="text-[#00FFA3] font-black text-lg flex items-center gap-1.5">
            <span>🔥 {data.value ?? 0}</span>
            <span className="text-xs font-normal text-gray-200">건/초</span>
            {data.status === 'PEAK' && (
              <span className="text-[10px] bg-red-500/20 text-red-400 border border-red-500/40 px-1 py-0.2 rounded font-bold">
                PEAK
              </span>
            )}
          </div>
          {data.viewerCount !== undefined && data.viewerCount !== null && (
            <div className="text-[#67BFFF] font-bold text-sm font-mono">
              👥 {Number(data.viewerCount).toLocaleString()} <span className="text-xs font-normal text-gray-100">명</span>
            </div>
          )}
        </div>
        {isFixedRealtime ? (
          <div className="text-[11px] text-gray-200 font-mono tracking-tighter">
            실제 시각: {formatTime(data.timestamp)}
          </div>
        ) : (
          <div className="flex flex-col mt-2">
            {data.offsetMs !== undefined && data.offsetMs !== null && (
              <div className="text-sm text-gray-100 font-bold mb-0.5 italic">
                🎬 {selectedTab === "realtime" ? "방송 진행" : "영상"} {formatOffset(data.offsetMs)}
              </div>
            )}
            <div className="text-[11px] text-gray-200 font-mono tracking-tighter">
              (방송 시각 {formatTime(data.timestamp)})
            </div>
          </div>
        )}

        {activeSeg && (
          <div className="mt-2 pt-2 border-t border-gray-700/60 flex flex-col gap-1">
            <span className="text-[10px] text-purple-300 font-bold uppercase tracking-wider">
              🎮 {activeSeg.categoryName}
            </span>
            <span className="text-xs text-white font-medium max-w-[200px] truncate block" title={activeSeg.title}>
              📝 {activeSeg.title}
            </span>
          </div>
        )}
      </div>
    );
  }
  return null;
};

const getCategoryColor = (category: string) => {
  const name = category.toLowerCase();

  if (name.includes('talk')) {
    return '#EAB308'
  }

  return '#8B5CF6'
};

export const AnalysisChart: React.FC<Props> = ({
  chartData, metric, viewerMetric, maxY, maxViewerY = 100, isLoading, isGathering, error, selectedTab,
  historyEmpty, onMouseMove, onMouseLeave, formatTime, rebangIndexes = [], segments = [], highlights = [],
  showTimeframeToggle = false, timeframe = 'realtime', onTimeframeChange
}) => {
  const isFixedRealtime = selectedTab === "realtime" && timeframe === "realtime";
  const isLiveTab = selectedTab === "realtime";

  const { processedData, uniqueColors } = React.useMemo(() => {
    if (isFixedRealtime || !segments.length || !chartData.length) {
      return { processedData: chartData, uniqueColors: ['#00FFA3'] };
    }

    const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);
    const newData = chartData.map(d => ({ ...d }));
    const colors = new Set<string>();

    for (let i = 0; i < newData.length; i++) {
      const d = newData[i];
      if (!d.timestamp) continue;
      
      const tsMs = normalize(d.timestamp);
      const activeSeg = segments.find(seg => {
        const start = new Date(seg.startedAt).getTime();
        const end = seg.endedAt ? new Date(seg.endedAt).getTime() : Infinity;
        return tsMs >= start && tsMs < end;
      });
      
      const color = activeSeg ? getCategoryColor(activeSeg.categoryName) : '#00FFA3';
      colors.add(color);
      
      d[`val_${color}`] = d.value;
      d._color = color;
    }

    for (let i = 1; i < newData.length; i++) {
      const prevColor = newData[i - 1]._color;
      const currColor = newData[i]._color;
      if (prevColor && currColor && prevColor !== currColor) {
        newData[i][`val_${prevColor}`] = newData[i].value;
      }
    }

    return { processedData: newData, uniqueColors: Array.from(colors) };
  }, [chartData, isFixedRealtime, segments]);


  return (
    <div className="p-3.5 sm:p-8 bg-[#0c0d0f] border border-gray-800 rounded-2xl sm:rounded-3xl relative overflow-hidden min-h-[440px] sm:min-h-[500px]">

      {error && (
        <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/80 backdrop-blur-sm">
          <div className="text-center p-6 bg-[#1a1a1c] border border-red-500/30 rounded-2xl">
            <p className="text-red-400 font-bold mb-2">데이터 연결 오류</p>
            <p className="text-gray-100 text-sm">{error}</p>
          </div>
        </div>
      )}

      {isLiveTab && (isLoading || isGathering) && !error && (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="w-10 h-10 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
          <p className="text-[#00FFA3] font-black tracking-widest text-sm">데이터 불러오는 중...</p>
        </div>
      )}

      {!isLiveTab && historyEmpty && (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
          <span className="text-4xl mb-4">🏜️</span>
          <p className="text-gray-100 font-bold tracking-widest">해당 날짜의 분석 기록이 없습니다.</p>
        </div>
      )}

      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6 sm:mb-8">
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4">
          <div>
            <h3 className="text-base sm:text-lg font-bold text-gray-200 uppercase tracking-widest italic">채팅 화력 및 시청자 추이</h3>
            <div className="flex items-center gap-4 mt-1.5 text-xs font-semibold">
              <span className="flex items-center gap-1.5 text-[#00FFA3]">
                <span className="w-2.5 h-2.5 rounded-sm bg-[#00FFA3]/80 inline-block" />
                채팅 화력 (건/초)
              </span>
              <span className="flex items-center gap-1.5 text-[#67BFFF]">
                <span className="w-3 h-0.5 bg-[#67BFFF] inline-block" />
                동시 시청자 (명)
              </span>
            </div>
          </div>

          {showTimeframeToggle && (
            <div className="inline-flex bg-[#16171a] p-1 rounded-xl border border-gray-800 text-[11px] sm:text-xs font-semibold self-start sm:self-auto sm:ml-2">
              <button
                type="button"
                onClick={() => onTimeframeChange?.('realtime')}
                className={`px-2.5 sm:px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5 ${
                  timeframe === 'realtime'
                    ? 'bg-[#24262b] text-[#00FFA3] font-bold border border-[#00FFA3]/30 shadow-sm'
                    : 'text-gray-100 hover:text-white'
                }`}
              >
                <span>⏱️</span>
                <span>실시간 (5분)</span>
              </button>
              <button
                type="button"
                onClick={() => onTimeframeChange?.('cumulative')}
                className={`px-2.5 sm:px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5 ${
                  timeframe === 'cumulative'
                    ? 'bg-[#24262b] text-[#00FFA3] font-bold border border-[#00FFA3]/30 shadow-sm'
                    : 'text-gray-100 hover:text-white'
                }`}
              >
                <span>📈</span>
                <span>전체 누적</span>
              </button>
            </div>
          )}
        </div>

        <div className="flex items-center gap-4 sm:gap-6 justify-between sm:justify-end w-full sm:w-auto border-t sm:border-t-0 border-gray-800/60 pt-3 sm:pt-0">
          {viewerMetric && (
            <div className="text-left sm:text-right">
              <div className="block mb-1 text-[11px] sm:text-xs text-gray-100 font-medium">{viewerMetric.label}</div>
              <span className="text-2xl sm:text-4xl font-black text-[#67BFFF] font-mono">
                {typeof viewerMetric.value === 'number' ? viewerMetric.value.toLocaleString() : viewerMetric.value}
                <span className="text-xs text-gray-100 ml-1.5 font-sans font-normal">명</span>
              </span>
            </div>
          )}
          <div className="text-right">
            <div className="block mb-1 text-[11px] sm:text-xs text-gray-100 font-medium">{metric.label}</div>
            <span className="text-2xl sm:text-4xl font-black text-[#00FFA3]">
              {metric.value} <span className="text-xs text-gray-100 ml-1 italic font-sans font-normal">msg/s</span>
            </span>
          </div>
        </div>
      </div>

      <div className="h-[280px] sm:h-[350px] lg:h-[400px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={processedData} margin={{ top: 10, right: 30, left: -20, bottom: 0 }} onMouseMove={onMouseMove} onMouseLeave={onMouseLeave}>
            <defs>
              <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#00FFA3" stopOpacity={0} />
              </linearGradient>
              {uniqueColors.map(color => (
                <linearGradient key={color} id={`grad_${color.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={color} stopOpacity={0.4} />
                  <stop offset="95%" stopColor={color} stopOpacity={0} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />
            {!isFixedRealtime && highlights.map((h) => {
              const hTs = new Date(h.startTime).getTime();
              const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);
              const idx = chartData.findIndex(d => d.timestamp && normalize(d.timestamp) >= hTs);
              if (idx === -1) return null;

              return (
                <ReferenceLine
                  key={`hl-${h.id}`}
                  x={chartData[idx]?.slotIndex ?? idx}
                  stroke="#F97316"
                  strokeDasharray="4 4"
                  strokeOpacity={0.85}
                  label={{ 
                    position: 'insideTop', 
                    value: '🔥 하이라이트', 
                    fill: '#F97316', 
                    fontSize: 10, 
                    fontWeight: 'bold',
                    offset: 15
                  }}
                />
              );
            })}
            <XAxis
              dataKey="slotIndex"
              tickFormatter={(idx) => {
                const ts = chartData[idx]?.timestamp;
                if (!ts) return "";
                return isFixedRealtime ? formatTime(ts) : formatShortTime(ts);
              }}
              interval="preserveStartEnd"
              minTickGap={isFixedRealtime ? 50 : 80}
              stroke="#475569"
              fontSize={10}
              tickMargin={15}
              axisLine={false}
              tickLine={false}
            />
            <YAxis yAxisId="firepower" stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />
            <YAxis
              yAxisId="viewers"
              orientation="right"
              stroke="#475569"
              fontSize={11}
              domain={[0, maxViewerY]}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => (v >= 10000 ? `${(v / 10000).toFixed(1)}만` : v >= 1000 ? `${(v / 1000).toFixed(1)}천` : `${v}`)}
            />

            {!isFixedRealtime && rebangIndexes.map((idx: number) => (
              <ReferenceLine
                key={idx}
                x={idx}
                stroke="#475569"
                strokeDasharray="5 5"
                label={{ position: 'insideTop', value: '⚡ RE-LIVE', fill: '#64748b', fontSize: 11, fontWeight: 'bold', offset: 15 }}
              />
            ))}

            {isFixedRealtime ? (
              <Area yAxisId="firepower" type="monotone" dataKey="value" stroke="#00FFA3" strokeWidth={3} fill="url(#colorValue)" isAnimationActive={false} connectNulls={false} />
            ) : (
              uniqueColors.map(color => (
                <Area 
                  key={color} 
                  yAxisId="firepower"
                  type="monotone" 
                  dataKey={`val_${color}`} 
                  stroke={color} 
                  strokeWidth={3} 
                  fill={`url(#grad_${color.replace('#', '')})`} 
                  isAnimationActive={false} 
                  connectNulls={false} 
                />
              ))
            )}
            
            <Line
              yAxisId="viewers"
              type="monotone"
              dataKey="viewerCount"
              stroke="#67BFFF"
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
              connectNulls={true}
            />

            <Tooltip content={<CustomTooltip selectedTab={selectedTab} timeframe={timeframe} formatTime={formatTime} segments={segments} />} cursor={{ stroke: "#00FFA3", strokeWidth: 1 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
