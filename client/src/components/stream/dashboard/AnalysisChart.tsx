import React from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';

interface Props {
  chartData: any[]; metric: any; maxY: number; isLoading: boolean;
  isGathering: boolean; error: string | null; selectedTab: string;
  historyEmpty: boolean; onMouseMove: (state: any) => void;
  onMouseLeave: () => void; formatTime: (ts: any) => string;
  rebangIndexes?: number[]; // ✨ 새로 추가된 프롭
}

// 🎬 VOD 상대 시간 포맷터 (00:00:00)
const formatOffset = (ms: number | undefined | null) => {
  if (ms === null || ms === undefined) return "--:--:--";
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  if (h > 0) return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

// 🕰️ 과거 탭용 심플 X축 포맷터 (오후 2:30)
const formatShortTime = (ts: any) => {
  if (!ts) return "";
  const d = typeof ts === 'number' ? (ts < 10000000000 ? new Date(ts * 1000) : new Date(ts)) : new Date(ts);
  return d.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
};

// ✨ 완벽 커스텀 툴팁
const CustomTooltip = ({ active, payload, selectedTab, formatTime }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    if (!data.hasData) return null;

    return (
        <div className="bg-[#1a1a1c] border border-gray-700 p-3 rounded-xl shadow-xl z-50">
          <div className="text-[#00FFA3] font-black text-lg mb-1">
            🔥 {data.value} <span className="text-xs font-normal text-gray-400">건/초</span>
          </div>
          {selectedTab === "realtime" ? (
              <div className="text-[11px] text-gray-400 font-mono tracking-tighter">
                실제 시각: {formatTime(data.timestamp)}
              </div>
          ) : (
              <div className="flex flex-col mt-2">
                <div className="text-sm text-gray-100 font-bold mb-0.5">
                  🎬 영상 {formatOffset(data.offsetMs)}
                </div>
                <div className="text-[11px] text-gray-500 font-mono tracking-tighter">
                  (방송 시각 {formatTime(data.timestamp)})
                </div>
              </div>
          )}
        </div>
    );
  }
  return null;
};

export const AnalysisChart: React.FC<Props> = ({
                                                 chartData, metric, maxY, isLoading, isGathering, error,
                                                 selectedTab, historyEmpty, onMouseMove, onMouseLeave, formatTime, rebangIndexes = []
                                               }) => {
  const isRealtime = selectedTab === "realtime";

  return (
      <div className="p-4 sm:p-8 bg-[#0c0d0f] border border-gray-800 rounded-3xl relative overflow-hidden min-h-[500px]">

        {error && (
            <div className="absolute inset-0 z-20 flex items-center justify-center bg-gray-900/80 backdrop-blur-sm">
              <div className="text-center p-6 bg-gray-800 border border-rose-500/30 rounded-xl">
                <p className="text-rose-400 font-bold mb-2 text-lg">연결 에러 발생</p>
                <p className="text-gray-300 text-sm">{error}</p>
              </div>
            </div>
        )}

        {isRealtime && (isLoading || isGathering || chartData.filter((d:any) => d.hasData).length === 0) && !error && (
            <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
              <div className="w-12 h-12 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin mb-4" />
              <p className="text-[#00FFA3] font-black tracking-widest">데이터 로딩 중...</p>
            </div>
        )}

        {!isRealtime && historyEmpty && (
            <div className="absolute inset-0 z-20 flex flex-col items-center justify-center bg-black/60 backdrop-blur-sm">
              <span className="text-5xl mb-4">🚧</span>
              <p className="text-gray-300 font-bold tracking-widest">해당 날짜의 데이터가 없습니다.</p>
            </div>
        )}

        <div className="flex justify-between items-start mb-10">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-bold text-gray-400 uppercase tracking-widest italic">채팅 화력 추이</h3>
          </div>
          <div className="text-right">
            <span className="text-[10px] text-[#a1a1aa] font-bold block mb-1 uppercase tracking-tighter">{metric.label}</span>
            <span className="text-4xl font-black text-[#00FFA3]">{metric.value} <span className="text-xs text-gray-600 ml-2 italic">건/초</span></span>
          </div>
        </div>

        <div className="h-[350px] w-full">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 15, right: 30, left: -20, bottom: 0 }} onMouseMove={onMouseMove} onMouseLeave={onMouseLeave}>
              <defs>
                <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3}/><stop offset="95%" stopColor="#00FFA3" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />

              {/* X축 Two-Track 전략 적용 */}
              <XAxis
                  dataKey="slotIndex"
                  tickFormatter={(idx) => {
                    const ts = chartData[idx]?.timestamp;
                    if (!ts) return "";
                    return isRealtime ? formatTime(ts) : formatShortTime(ts);
                  }}
                  interval={0}
                  minTickGap={isRealtime ? 35 : 60} // 실시간은 촘촘하게, 과거는 널찍하게
                  stroke="#475569"
                  fontSize={10}
                  tickMargin={15}
                  axisLine={false}
                  tickLine={false}
              />

              <YAxis stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />

              <Tooltip content={<CustomTooltip selectedTab={selectedTab} formatTime={formatTime} />} cursor={{ stroke: "#00FFA3", strokeWidth: 1, strokeDasharray: '4 4' }} />

              {/* 리방 시점을 감지하여 캔버스에 세로 절취선(ReferenceLine) 렌더링 */}
              {rebangIndexes.map(idx => (
                  <ReferenceLine
                      key={idx}
                      x={idx}
                      stroke="#71717a"
                      strokeDasharray="4 4"
                      label={{ position: 'top', value: '⚡ 리방', fill: '#a1a1aa', fontSize: 10, fontWeight: 'bold' }}
                  />
              ))}

              <Area type="monotone" dataKey="value" stroke="#00FFA3" strokeWidth={4} fill="url(#colorValue)" isAnimationActive={false} connectNulls={false} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>
  );
};
