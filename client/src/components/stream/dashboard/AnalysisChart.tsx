// src/components/stream/dashboard/AnalysisChart.tsx

import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, ReferenceArea } from 'recharts';
import type { StreamSegment } from '../../../types/StreamSegment';

interface Props {
  chartData: any[];
  metric: {
    label: React.ReactNode;
    value: string | number
  };
  maxY: number;
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

const CustomTooltip = ({ active, payload, selectedTab, formatTime }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    if (!data.hasData) return null;

    return (
      <div className="bg-[#1a1a1c] border border-gray-700 p-3 rounded-xl shadow-2xl z-50">
        <div className="text-[#00FFA3] font-black text-lg mb-1">
          🔥 {data.value} <span className="text-xs font-normal text-gray-200">건/초</span>
        </div>
        {selectedTab === "realtime" ? (
          <div className="text-[11px] text-gray-200 font-mono tracking-tighter">
            실제 시각: {formatTime(data.timestamp)}
          </div>
        ) : (
          <div className="flex flex-col mt-2">
            <div className="text-sm text-gray-100 font-bold mb-0.5 italic">
              🎬 영상 {formatOffset(data.offsetMs)}
            </div>
            <div className="text-[11px] text-gray-200 font-mono tracking-tighter">
              (방송 시각 {formatTime(data.timestamp)})
            </div>
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

const getSegmentXRange = (seg: StreamSegment, data: any[]) => {
  const startTs = new Date(seg.startedAt).getTime();
  const endTs = seg.endedAt ? new Date(seg.endedAt).getTime() : Infinity;

  let x1 = data.findIndex(d => d.timestamp && d.timestamp >= startTs);
  if (x1 === -1) x1 = 0;

  let x2 = data.findIndex(d => d.timestamp && d.timestamp >= endTs);
  if (x2 === -1) x2 = data.length - 1;

  return { x1, x2 };
};

export const AnalysisChart: React.FC<Props> = ({
  chartData, metric, maxY, isLoading, isGathering, error, selectedTab,
  historyEmpty, onMouseMove, onMouseLeave, formatTime, rebangIndexes = [], segments = []
}) => {
  const isRealtime = selectedTab === "realtime";

  return (
    <div className="p-4 sm:p-8 bg-[#0c0d0f] border border-gray-800 rounded-3xl relative overflow-hidden min-h-[500px]">

      {error && (
        <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/80 backdrop-blur-sm">
          <div className="text-center p-6 bg-[#1a1a1c] border border-red-500/30 rounded-2xl">
            <p className="text-red-400 font-bold mb-2">데이터 연결 오류</p>
            <p className="text-gray-400 text-sm">{error}</p>
          </div>
        </div>
      )}

      {isRealtime && (isLoading || isGathering) && !error && (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="w-10 h-10 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
          <p className="text-[#00FFA3] font-black tracking-widest text-sm">데이터 불러오는 중...</p>
        </div>
      )}

      {!isRealtime && historyEmpty && (
        <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
          <span className="text-4xl mb-4">🏜️</span>
          <p className="text-gray-400 font-bold tracking-widest">해당 날짜의 분석 기록이 없습니다.</p>
        </div>
      )}

      <div className="flex justify-between items-start mb-10">
        <h3 className="text-lg font-bold text-gray-200 uppercase tracking-widest italic">채팅 화력 추이</h3>
        <div className="text-right">
          <div className="block mb-1">{metric.label}</div>
          <span className="text-4xl font-black text-[#00FFA3]">
            {metric.value} <span className="text-xs text-gray-300 ml-2 italic">msg/s</span>
          </span>
        </div>
      </div>

      <div className="h-[350px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 10, right: 30, left: -20, bottom: 0 }} onMouseMove={onMouseMove} onMouseLeave={onMouseLeave}>
            <defs>
              <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#00FFA3" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />
            {!isRealtime && segments.map((seg) => {
              const { x1, x2 } = getSegmentXRange(seg, chartData);
              return (
                <ReferenceArea
                  key={seg.id}
                  x1={x1}
                  x2={x2}
                  y1={0}
                  y2={maxY}
                  fill={getCategoryColor(seg.categoryName)}
                  fillOpacity={0.08}
                  stroke="none"
                />
              );
            })}
            <XAxis
              dataKey="slotIndex"
              tickFormatter={(idx) => {
                const ts = chartData[idx]?.timestamp;
                if (!ts) return "";
                return isRealtime ? formatTime(ts) : formatShortTime(ts);
              }}
              interval="preserveStartEnd"
              minTickGap={isRealtime ? 50 : 80}
              stroke="#475569"
              fontSize={10}
              tickMargin={15}
              axisLine={false}
              tickLine={false}
            />
            <YAxis stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />

            {!isRealtime && rebangIndexes.map((idx: number) => (
              <ReferenceLine
                key={idx}
                x={idx}
                stroke="#475569"
                strokeDasharray="5 5"
                label={{ position: 'top', value: '⚡ RE-LIVE', fill: '#64748b', fontSize: 10, fontWeight: 'bold' }}
              />
            ))}

            <Area type="monotone" dataKey="value" stroke="#00FFA3" strokeWidth={3} fill="url(#colorValue)" isAnimationActive={false} connectNulls={false} />
            <Tooltip content={<CustomTooltip selectedTab={selectedTab} formatTime={formatTime} />} cursor={{ stroke: "#00FFA3", strokeWidth: 1 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
