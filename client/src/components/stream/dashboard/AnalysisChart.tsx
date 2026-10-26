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
            <span>🔥 {data.rawValue ?? data.value ?? 0}</span>
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
            <div className="flex items-center justify-between gap-1">
              <span className="text-[10px] text-purple-300 font-bold uppercase tracking-wider">
                🎮 {activeSeg.categoryName}
              </span>
              {activeSeg.isAdult && (
                <span className="text-[10px] bg-rose-500/20 text-rose-400 border border-rose-500/40 px-1 py-0.2 rounded font-bold">
                  🔞 19금
                </span>
              )}
            </div>
            <span className="text-xs text-white font-medium max-w-[200px] truncate block" title={activeSeg.title}>
              📝 {activeSeg.title}
            </span>
            {activeSeg.isAdult && (
              <span className="text-[10px] text-rose-300/80 italic">
                (채팅 화력 수집 제외 구간)
              </span>
            )}
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
    const sorted = [...segments].sort(
      (a, b) => new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime()
    );
    const merged: Array<{
      id: any;
      categoryName: string;
      titles: string[];
      startedAt: string;
      endedAt: string | null;
      isAdult?: boolean;
    }> = [];

    for (const seg of sorted) {
      const cat = seg.categoryName || '기타';
      const last = merged[merged.length - 1];
      if (last && last.categoryName === cat && Boolean(last.isAdult) === Boolean(seg.isAdult)) {
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
          endedAt: seg.endedAt,
          isAdult: seg.isAdult
        });
      }
    }
    return merged;
  }, [segments]);

  const categoryColorMap = React.useMemo(() => {
    return buildCategoryColorMap(segments);
  }, [segments]);

  const [showFirepower, setShowFirepower] = React.useState(true);
  const [showViewers, setShowViewers] = React.useState(true);
  const [isSmooth, setIsSmooth] = React.useState(true);

  const toggleFirepower = () => {
    if (showFirepower && !showViewers) return;
    setShowFirepower(prev => !prev);
  };

  const toggleViewers = () => {
    if (showViewers && !showFirepower) return;
    setShowViewers(prev => !prev);
  };

  const hasAnyFirepower = React.useMemo(() => {
    return chartData.some(d => d.value > 0);
  }, [chartData]);

  const processedData = React.useMemo(() => {
    if (!chartData || chartData.length === 0) return [];

    return chartData.map((d, i, arr) => {
      const rawFirepower = d.value ?? 0;
      let smoothedFirepower = rawFirepower;

      if (isSmooth && arr.length >= 3) {
        let weightSum = 0;
        let valSum = 0;
        for (let offset = -2; offset <= 2; offset++) {
          const idx = i + offset;
          if (idx >= 0 && idx < arr.length) {
            const w = offset === 0 ? 0.4 : Math.abs(offset) === 1 ? 0.2 : 0.1;
            valSum += (arr[idx].value ?? 0) * w;
            weightSum += w;
          }
        }
        smoothedFirepower = weightSum > 0 ? Math.round((valSum / weightSum) * 10) / 10 : rawFirepower;
      }

      return {
        ...d,
        rawValue: rawFirepower,
        displayFirepower: isSmooth ? smoothedFirepower : rawFirepower,
      };
    });
  }, [chartData, isSmooth]);

  const ribbonSegments = React.useMemo(() => {
    if (!mergedSegments.length) return [];
    const now = Date.now();
    const normalize = (ts: number) => (ts < 10000000000 ? ts * 1000 : ts);

    if (chartData.length > 0) {
      const chartStartTs = normalize(chartData[0].timestamp);
      const chartEndTs = normalize(chartData[chartData.length - 1].timestamp);
      const chartDuration = Math.max(1, chartEndTs - chartStartTs);

      const overlapping = [];
      for (const seg of mergedSegments) {
        const segStartMs = new Date(seg.startedAt).getTime();
        const segEndMs = seg.endedAt ? new Date(seg.endedAt).getTime() : now;

        if (segEndMs <= chartStartTs || segStartMs >= chartEndTs) continue;

        const clampedStart = Math.max(chartStartTs, segStartMs);
        const clampedEnd = Math.min(chartEndTs, segEndMs);
        if (clampedEnd <= clampedStart) continue;

        overlapping.push({
          ...seg,
          rawStartMs: segStartMs,
          rawEndMs: segEndMs,
          clampedStart,
          clampedEnd,
          durationMs: clampedEnd - clampedStart,
        });
      }

      if (overlapping.length === 0) {
        const fallback = mergedSegments[mergedSegments.length - 1];
        return [{
          ...fallback,
          startMs: chartStartTs,
          endMs: chartEndTs,
          durationMs: chartDuration,
          leftPercent: 0,
          widthPercent: 100,
          color: categoryColorMap.get(fallback.categoryName) || '#8B5CF6',
          timeRangeLabel: `${formatClockTime(fallback.startedAt)} ~ ${fallback.endedAt ? formatClockTime(fallback.endedAt) : '현재'}`,
          durationLabel: formatDuration(chartDuration)
        }];
      }

      const result = [];
      for (let i = 0; i < overlapping.length; i++) {
        const seg = overlapping[i];
        const rawLeft = ((seg.clampedStart - chartStartTs) / chartDuration) * 100;
        const rawRight = ((seg.clampedEnd - chartStartTs) / chartDuration) * 100;

        const leftPercent = i === 0 ? 0 : rawLeft;
        const rightPercent = i === overlapping.length - 1 ? 100 : rawRight;
        const widthPercent = Math.max(0.1, rightPercent - leftPercent);

        result.push({
          ...seg,
          startMs: seg.rawStartMs,
          endMs: seg.rawEndMs,
          leftPercent,
          widthPercent,
          color: categoryColorMap.get(seg.categoryName) || '#8B5CF6',
          timeRangeLabel: `${formatClockTime(seg.startedAt)} ~ ${seg.endedAt ? formatClockTime(seg.endedAt) : '현재'}`,
          durationLabel: formatDuration(seg.durationMs)
        });
      }

      for (let i = 0; i < result.length - 1; i++) {
        const nextLeft = result[i + 1].leftPercent;
        result[i].widthPercent = Math.max(0.1, nextLeft - result[i].leftPercent);
      }

      return result;
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
    return parsed.map((s, idx) => {
      const leftPercent = (accumulatedMs / totalDuration) * 100;
      const widthPercent = idx === parsed.length - 1 ? (100 - leftPercent) : (s.durationMs / totalDuration) * 100;
      accumulatedMs += s.durationMs;
      return {
        ...s,
        leftPercent,
        widthPercent
      };
    });
  }, [mergedSegments, chartData, categoryColorMap]);

  const xTicks = React.useMemo(() => {
    if (!chartData || chartData.length === 0) return [];
    if (chartData.length <= 6) return chartData.map((_, i) => i);
    const tickCount = 6;
    const step = (chartData.length - 1) / (tickCount - 1);
    const ticks: number[] = [];
    for (let i = 0; i < tickCount; i++) {
      ticks.push(Math.min(chartData.length - 1, Math.round(i * step)));
    }
    return Array.from(new Set(ticks));
  }, [chartData]);


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
            <div className="flex flex-wrap items-center gap-2 sm:gap-3 mt-2 text-xs">
              <button
                type="button"
                onClick={toggleFirepower}
                className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border transition-all text-xs font-semibold ${
                  showFirepower
                    ? 'bg-[#00FFA3]/10 border-[#00FFA3]/40 text-[#00FFA3]'
                    : 'bg-gray-800/40 border-gray-700/60 text-gray-500 line-through opacity-60'
                }`}
                title="채팅 화력 그래프 표시/숨김"
              >
                <span className={`w-2 h-2 rounded-full ${showFirepower ? 'bg-[#00FFA3]' : 'bg-gray-500'}`} />
                채팅 화력 (건/초)
              </button>

              <button
                type="button"
                onClick={toggleViewers}
                className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg border transition-all text-xs font-semibold ${
                  showViewers
                    ? 'bg-[#67BFFF]/10 border-[#67BFFF]/40 text-[#67BFFF]'
                    : 'bg-gray-800/40 border-gray-700/60 text-gray-500 line-through opacity-60'
                }`}
                title="시청자 수 그래프 표시/숨김"
              >
                <span className={`w-2.5 h-0.5 ${showViewers ? 'bg-[#67BFFF]' : 'bg-gray-500'}`} />
                동시 시청자 (명)
              </button>

              <button
                type="button"
                onClick={() => setIsSmooth(prev => !prev)}
                className={`flex items-center gap-1 px-2.5 py-1 rounded-lg border transition-all text-xs font-medium ${
                  isSmooth
                    ? 'bg-purple-500/15 border-purple-500/40 text-purple-300'
                    : 'bg-gray-800/40 border-gray-700/60 text-gray-400'
                }`}
                title="가시밭 노이즈를 완화하고 흐름을 부드럽게 봅니다"
              >
                <span>{isSmooth ? '✨ 부드러운 곡선' : '⚡ 1분 원본'}</span>
              </button>
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
                    <span className="text-[11px] font-bold text-gray-200 truncate flex items-center gap-1">
                      {seg.isAdult && <span title="연령 제한(19금) 구간">🔞</span>}
                      {seg.categoryName}
                    </span>
                  </div>

                  {/* 마우스 호버 시 상세 툴팁 */}
                  <div className="pointer-events-none absolute bottom-full mb-2 left-1/2 -translate-x-1/2 hidden group-hover:flex flex-col items-center z-50 whitespace-nowrap">
                    <div className="bg-[#1a1b1e] border border-gray-700/80 text-white px-3 py-2 rounded-xl shadow-2xl text-xs flex flex-col gap-1 min-w-[130px]">
                      <div className="flex items-center gap-1.5 font-bold text-white">
                        <span className="w-2 h-2 rounded-full" style={{ backgroundColor: seg.color }} />
                        <span>{seg.categoryName}</span>
                        {seg.isAdult && <span className="text-[10px] text-rose-400 font-bold ml-1">🔞 19금</span>}
                      </div>
                      <div className="text-[11px] text-gray-100 font-mono">
                        ⏱️ {seg.durationLabel} ({seg.timeRangeLabel})
                      </div>
                      {seg.isAdult && (
                        <div className="text-[10px] text-rose-400 font-medium">
                          채팅 화력 수집 제외 구간
                        </div>
                      )}
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
              <linearGradient id="colorFirepowerArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.18} />
                <stop offset="95%" stopColor="#00FFA3" stopOpacity={0.0} />
              </linearGradient>
              <linearGradient id="colorViewerArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#67BFFF" stopOpacity={0.25} />
                <stop offset="95%" stopColor="#67BFFF" stopOpacity={0.02} />
              </linearGradient>
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
              ticks={xTicks}
              interval={0}
              tickFormatter={(idx) => {
                const ts = chartData[idx]?.timestamp;
                if (!ts) return "";
                return formatShortTime(ts);
              }}
              stroke="#475569"
              fontSize={10}
              tickMargin={15}
              axisLine={false}
              tickLine={false}
            />
            {showFirepower && (
              <YAxis yAxisId="firepower" width={40} stroke="#475569" fontSize={11} domain={[0, maxY]} axisLine={false} tickLine={false} />
            )}
            {showViewers && (
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
            )}

            {rebangIndexes.map((idx: number) => (
              <ReferenceLine
                key={idx}
                x={idx}
                stroke="#475569"
                strokeDasharray="5 5"
                label={{ position: 'insideTop', value: '⚡ RE-LIVE', fill: '#64748b', fontSize: 11, fontWeight: 'bold', offset: 15 }}
              />
            ))}

            {showFirepower && hasAnyFirepower && (
              <Area 
                yAxisId="firepower" 
                type="monotone" 
                dataKey="displayFirepower" 
                stroke="#00FFA3" 
                strokeWidth={2} 
                fill="url(#colorFirepowerArea)" 
                isAnimationActive={false} 
                connectNulls={true} 
              />
            )}
            
            {showViewers && (
              (!showFirepower || !hasAnyFirepower) ? (
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
                  strokeWidth={2.5}
                  dot={false}
                  isAnimationActive={false}
                  connectNulls={true}
                />
              )
            )}

            <Tooltip content={<CustomTooltip selectedTab={selectedTab} formatTime={formatTime} segments={segments} />} cursor={{ stroke: "#00FFA3", strokeWidth: 1 }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};
