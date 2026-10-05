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

const resolveActiveSegment = (tsMs: number, segments: any[]) => {
  if (!segments || segments.length === 0) return null;

  const exact = segments.find((seg: any) => {
    const start = new Date(seg.startedAt).getTime();
    const end = seg.endedAt ? new Date(seg.endedAt).getTime() : Infinity;
    return tsMs >= start && tsMs < end;
  });
  if (exact) return exact;

  const firstStart = new Date(segments[0].startedAt).getTime();
  if (tsMs <= firstStart) {
    return segments[0];
  }

  for (let i = segments.length - 1; i >= 0; i--) {
    if (new Date(segments[i].startedAt).getTime() <= tsMs) {
      return segments[i];
    }
  }

  return segments[0];
};

const CustomTooltip = ({ active, payload, selectedTab, formatTime, segments = [] }: any) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    if (!data.hasData) return null;

    const tsMs = data.timestamp < 10000000000 ? data.timestamp * 1000 : data.timestamp;
    const activeSeg = resolveActiveSegment(tsMs, segments);

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

const GAME_PALETTE = ['#8B5CF6', '#06B6D4', '#10B981', '#F43F5E', '#F97316', '#3B82F6', '#A855F7'];

const buildCategoryColorMap = (segments: StreamSegment[]) => {
  const colorMap = new Map<string, string>();
  let gameColorIndex = 0;

  for (const seg of segments) {
    const cat = seg.categoryName || '기타';
    if (!colorMap.has(cat)) {
      if (cat.toLowerCase().includes('talk')) {
        colorMap.set(cat, '#EAB308');
      } else {
        colorMap.set(cat, GAME_PALETTE[gameColorIndex % GAME_PALETTE.length]);
        gameColorIndex++;
      }
    }
  }
  return colorMap;
};

const formatDuration = (ms: number) => {
  if (ms <= 0) return "";
  const totalMin = Math.round(ms / 60000);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h > 0 && m > 0) return `${h}시간 ${m}분`;
  if (h > 0) return `${h}시간`;
  return `${m}분`;
};

const formatClockTime = (isoString: string | null) => {
  if (!isoString) return '현재';
  const d = new Date(isoString);
  return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
};

export const AnalysisChart: React.FC<Props> = ({
  chartData, metric, viewerMetric, maxY, maxViewerY = 100, isLoading, isGathering, error, selectedTab,
  historyEmpty, onMouseMove, onMouseLeave, formatTime, rebangIndexes = [], segments = [], highlights = []
}) => {
  const isLiveTab = selectedTab === "realtime";

  const mergedSegments = React.useMemo(() => {
    if (!segments || segments.length === 0) return [];
    const merged: Array<{
      id: any;
      categoryName: string;
      titles: string[];
      startedAt: string;
      endedAt: string | null;
    }> = [];

    for (const seg of segments) {
      const cat = seg.categoryName || '기타';
      const last = merged[merged.length - 1];
      if (last && last.categoryName === cat) {
        last.endedAt = seg.endedAt;
        if (seg.title && !last.titles.includes(seg.title)) {
          last.titles.push(seg.title);
        }
      } else {
        merged.push({
          id: seg.id ?? `${cat}-${seg.startedAt}`,
          categoryName: cat,
          titles: seg.title ? [seg.title] : [],
          startedAt: seg.startedAt,
          endedAt: seg.endedAt
        });
      }
    }
    return merged;
  }, [segments]);

  const categoryColorMap = React.useMemo(() => {
    return buildCategoryColorMap(segments);
  }, [segments]);

  const hasAnyFirepower = React.useMemo(() => {
    return chartData.some(d => d.value > 0);
  }, [chartData]);

  const { processedData, uniqueColors } = React.useMemo(() => {
    if (!segments.length || !chartData.length) {
      return { processedData: chartData, uniqueColors: ['#00FFA3'] };
    }

    const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);
    const newData = chartData.map(d => ({ ...d }));
    const colors = new Set<string>();

    for (let i = 0; i < newData.length; i++) {
      const d = newData[i];
      if (!d.timestamp) continue;
      
      const tsMs = normalize(d.timestamp);
      const activeSeg = resolveActiveSegment(tsMs, segments);
      
      const color = activeSeg ? (categoryColorMap.get(activeSeg.categoryName) || '#8B5CF6') : '#00FFA3';
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
  }, [chartData, segments, categoryColorMap]);

  const ribbonSegments = React.useMemo(() => {
    if (!mergedSegments.length) return [];
    const now = Date.now();
    const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);

    if (chartData.length > 0) {
      const totalSlots = chartData.length;
      const chartStartTs = normalize(chartData[0].timestamp);
      const chartEndTs = normalize(chartData[chartData.length - 1].timestamp);

      const validSegments = [];
      for (let sIdx = 0; sIdx < mergedSegments.length; sIdx++) {
        const seg = mergedSegments[sIdx];
        const segStartMs = new Date(seg.startedAt).getTime();
        const segEndMs = seg.endedAt ? new Date(seg.endedAt).getTime() : now;

        if (segEndMs <= chartStartTs || segStartMs >= chartEndTs) continue;

        let startSlot = 0;
        if (sIdx > 0 && segStartMs > chartStartTs) {
          const foundIdx = chartData.findIndex(d => d.timestamp && normalize(d.timestamp) >= segStartMs);
          startSlot = foundIdx !== -1 ? foundIdx : 0;
        }

        let endSlot = totalSlots;
        if (seg.endedAt && segEndMs < chartEndTs && sIdx < mergedSegments.length - 1) {
          const foundEndIdx = chartData.findIndex(d => d.timestamp && normalize(d.timestamp) >= segEndMs);
          endSlot = foundEndIdx !== -1 ? foundEndIdx : totalSlots;
        }

        const slotCount = Math.max(1, endSlot - startSlot);
        const duration = Math.max(0, segEndMs - segStartMs);

        validSegments.push({
          ...seg,
          startMs: segStartMs,
          endMs: segEndMs,
          durationMs: duration,
          startSlot,
          endSlot,
          leftPercent: (startSlot / totalSlots) * 100,
          widthPercent: (slotCount / totalSlots) * 100,
          color: categoryColorMap.get(seg.categoryName) || '#8B5CF6',
          timeRangeLabel: `${formatClockTime(seg.startedAt)} ~ ${seg.endedAt ? formatClockTime(seg.endedAt) : '현재'}`,
          durationLabel: formatDuration(duration)
        });
      }

      return validSegments;
    }

    const parsed = mergedSegments.map(seg => {
      const start = new Date(seg.startedAt).getTime();
      const end = seg.endedAt ? new Date(seg.endedAt).getTime() : now;
      const duration = Math.max(0, end - start);
      return {
        ...seg,
        startMs: start,
        endMs: end,
        durationMs: duration,
        color: categoryColorMap.get(seg.categoryName) || '#8B5CF6',
        timeRangeLabel: `${formatClockTime(seg.startedAt)} ~ ${seg.endedAt ? formatClockTime(seg.endedAt) : '현재'}`,
        durationLabel: formatDuration(duration)
      };
    });

    const totalDuration = parsed.reduce((acc, s) => acc + s.durationMs, 0);
    if (totalDuration <= 0) return [];

    let accumulatedMs = 0;
    return parsed.map(s => {
      const leftPercent = (accumulatedMs / totalDuration) * 100;
      const widthPercent = (s.durationMs / totalDuration) * 100;
      accumulatedMs += s.durationMs;
      return {
        ...s,
        leftPercent,
        widthPercent
      };
    });
  }, [mergedSegments, chartData, categoryColorMap]);


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

      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-4">
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

      {ribbonSegments.length > 0 && (
        <div style={{ paddingLeft: '40px', paddingRight: '48px' }} className="w-full mb-5">
          <div className="w-full h-7 bg-[#141518] rounded-lg border border-gray-800/80 relative z-10">
            {ribbonSegments.map((seg, idx) => {
              const isFirst = seg.leftPercent <= 0.1;
              const isLast = seg.leftPercent + seg.widthPercent >= 99.9;
              return (
                <div
                  key={seg.id || idx}
                  style={{
                    left: `${seg.leftPercent}%`,
                    width: `${seg.widthPercent}%`,
                    backgroundColor: `${seg.color}25`
                  }}
                  className={`absolute top-0 bottom-0 flex items-center justify-center px-1.5 border-r border-gray-800/80 group cursor-default transition-all hover:brightness-125 ${
                    isFirst ? 'rounded-l-lg' : ''
                  } ${
                    isLast ? 'rounded-r-lg border-r-0' : ''
                  }`}
                >
                  <div className="flex items-center gap-1.5 min-w-0 max-w-full">
                    <span
                      className="w-2 h-2 rounded-full shrink-0"
                      style={{ backgroundColor: seg.color }}
                    />
                    <span className="text-[11px] font-bold text-gray-200 truncate">
                      {seg.categoryName}
                    </span>
                  </div>

                  {/* 마우스 호버 시 상세 툴팁 */}
                  <div className="pointer-events-none absolute bottom-full mb-2 left-1/2 -translate-x-1/2 hidden group-hover:flex flex-col items-center z-50 whitespace-nowrap">
                    <div className="bg-[#1a1b1e] border border-gray-700/80 text-white px-3 py-2 rounded-xl shadow-2xl text-xs flex flex-col gap-1 min-w-[130px]">
                      <div className="flex items-center gap-1.5 font-bold text-white">
                        <span className="w-2 h-2 rounded-full" style={{ backgroundColor: seg.color }} />
                        <span>{seg.categoryName}</span>
                      </div>
                      <div className="text-[11px] text-gray-100 font-mono">
                        ⏱️ {seg.durationLabel} ({seg.timeRangeLabel})
                      </div>
                      {seg.titles && seg.titles.length > 0 && (
                        <div className="text-[10px] text-gray-200 max-w-[220px] truncate">
                          📝 {seg.titles.join(' / ')}
                        </div>
                      )}
                    </div>
                    <div className="w-2 h-2 bg-[#1a1b1e] border-r border-b border-gray-700/80 rotate-45 -mt-1" />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div className="h-[280px] sm:h-[350px] lg:h-[400px] w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={processedData} margin={{ top: 10, right: 0, left: 0, bottom: 0 }} onMouseMove={onMouseMove} onMouseLeave={onMouseLeave}>
            <defs>
              <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#00FFA3" stopOpacity={0} />
              </linearGradient>
              <linearGradient id="colorViewerArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#67BFFF" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#67BFFF" stopOpacity={0.02} />
              </linearGradient>
              {uniqueColors.map(color => (
                <linearGradient key={color} id={`grad_${color.replace('#', '')}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={color} stopOpacity={0.4} />
                  <stop offset="95%" stopColor={color} stopOpacity={0} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.3} />
            {highlights.map((h) => {
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
                return formatShortTime(ts);
              }}
              interval="preserveStartEnd"
              minTickGap={80}
              stroke="#475569"
              fontSize={10}
              tickMargin={15}
              axisLine={false}
              tickLine={false}
            />
            <YAxis yAxisId="firepower" width={40} stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />
            <YAxis
              yAxisId="viewers"
              orientation="right"
              width={48}
              stroke="#475569"
              fontSize={11}
              domain={[0, maxViewerY]}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => (v >= 10000 ? `${(v / 10000).toFixed(1)}만` : v >= 1000 ? `${(v / 1000).toFixed(1)}천` : `${v}`)}
            />

            {rebangIndexes.map((idx: number) => (
              <ReferenceLine
                key={idx}
                x={idx}
                stroke="#475569"
                strokeDasharray="5 5"
                label={{ position: 'insideTop', value: '⚡ RE-LIVE', fill: '#64748b', fontSize: 11, fontWeight: 'bold', offset: 15 }}
              />
            ))}

            {hasAnyFirepower && (
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
            
            {!hasAnyFirepower ? (
              <Area 
                yAxisId="viewers" 
                type="monotone" 
                dataKey="viewerCount" 
                stroke="#67BFFF" 
                strokeWidth={2.5} 
                fill="url(#colorViewerArea)" 
                isAnimationActive={false} 
                connectNulls={true} 
              />
            ) : (
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
            )}

            <Tooltip content={<CustomTooltip selectedTab={selectedTab} formatTime={formatTime} segments={segments} />} cursor={{ stroke: "#00FFA3", strokeWidth: 1 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
