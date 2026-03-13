// src/components/stream/dashboard/AnalysisChart.tsx
import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface Props {
  chartData: any[]; metric: any; maxY: number; isLoading: boolean;
  isGathering: boolean; error: string | null; selectedTab: string;
  historyEmpty: boolean; onMouseMove: (state: any) => void;
  onMouseLeave: () => void; formatTime: (ts: any) => string;
}

export const AnalysisChart: React.FC<Props> = ({
                                                 chartData, metric, maxY, isLoading, isGathering, error,
                                                 selectedTab, historyEmpty, onMouseMove, onMouseLeave, formatTime
                                               }) => (
    <div className="p-8 bg-[#0c0d0f] border border-gray-800 rounded-3xl relative overflow-hidden min-h-[500px]">

      {/* 💡 [에러 UI 추가] error 값을 읽어서 화면에 표시 (TS6133 해결) */}
      {error && (
          <div className="absolute inset-0 z-20 flex items-center justify-center bg-gray-900/80 backdrop-blur-sm">
            <div className="text-center p-6 bg-gray-800 border border-rose-500/30 rounded-xl">
              <p className="text-rose-400 font-bold mb-2 text-lg">연결 에러 발생</p>
              <p className="text-gray-300 text-sm">{error}</p>
            </div>
          </div>
      )}

      {/* 로딩 표시 (에러가 없을 때만 표시) */}
      {selectedTab === "realtime" && (isLoading || isGathering || chartData.filter((d:any) => d.hasData).length === 0) && !error && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
            <div className="w-12 h-12 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-[#00FFA3] font-black tracking-widest">데이터 로딩 중...</p>
          </div>
      )}

      {selectedTab !== "realtime" && historyEmpty && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
            <span className="text-5xl mb-4">🚧</span>
            <p className="text-gray-300 font-bold tracking-widest">과거 데이터 조회 API 연결 준비 중입니다.</p>
          </div>
      )}

      <div className="flex justify-between items-start mb-10">
        <h3 className="text-lg font-bold text-gray-400 uppercase tracking-widest italic">채팅 화력 추이</h3>
        <div className="text-right">
          <span className="text-[10px] text-gray-500 font-bold block mb-1 uppercase tracking-tighter">{metric.label}</span>
          <span className="text-4xl font-black text-[#00FFA3]">{metric.value} <span className="text-xs text-gray-600 ml-2 italic">건/초</span></span>
        </div>
      </div>

      <div className="h-[350px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }} onMouseMove={onMouseMove} onMouseLeave={onMouseLeave}>
            <defs>
              <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3}/><stop offset="95%" stopColor="#00FFA3" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />
            <XAxis dataKey="slotIndex" tickFormatter={(idx) => chartData[idx]?.timestamp ? formatTime(chartData[idx].timestamp) : ""} interval={0} minTickGap={5} stroke="#475569" fontSize={10} tickMargin={15} axisLine={false} tickLine={false} />
            <YAxis stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ stroke: "#00FFA3", strokeWidth: 1, strokeDasharray: '4 4' }} contentStyle={{ backgroundColor: "#1a1a1c", border: '1px solid #334155', borderRadius: '12px' }} labelFormatter={(idx) => chartData[idx as number]?.timestamp ? `시점: ${formatTime(chartData[idx as number].timestamp)}` : ''} formatter={(val) => [`${val} 건/초`, '화력']} />
            <Area type="monotone" dataKey="value" stroke="#00FFA3" strokeWidth={4} fill="url(#colorValue)" isAnimationActive={false} connectNulls={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
);
