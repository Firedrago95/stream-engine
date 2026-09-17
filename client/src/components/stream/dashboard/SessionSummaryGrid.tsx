import React, { useMemo } from 'react';

interface SessionSummaryGridProps {
  isLive: boolean;
  startedAt?: string | null;
  endedAt?: string | null;
  currentViewers?: number;
  timeline?: Array<{ timestamp: number; viewerCount: number }>;
  dataPoints?: Array<{ timestamp: number; viewerCount?: number; value?: number }>;
  summaryAvg?: number | null;
  summaryPeak?: number | null;
  subscriberChatRate?: number | null;
}

const formatDuration = (ms: number): string => {
  if (ms <= 0 || isNaN(ms)) return '0분';
  const totalMinutes = Math.floor(ms / (1000 * 60));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours > 0) {
    return `${hours}시간 ${minutes}분`;
  }
  return `${minutes}분`;
};

export const SessionSummaryGrid: React.FC<SessionSummaryGridProps> = ({
  isLive,
  startedAt,
  endedAt,
  currentViewers = 0,
  timeline = [],
  dataPoints = [],
  summaryAvg = null,
  summaryPeak = null,
  subscriberChatRate = null
}) => {
  const durationText = useMemo(() => {
    if (!startedAt) return '--';

    const startTs = new Date(startedAt).getTime();
    if (isNaN(startTs)) return '--';

    if (isLive) {
      const nowTs = Date.now();
      const diff = Math.max(0, nowTs - startTs);
      return formatDuration(diff);
    }

    if (endedAt) {
      const endTs = new Date(endedAt).getTime();
      if (!isNaN(endTs)) {
        return formatDuration(Math.max(0, endTs - startTs));
      }
    }

    // endedAt이 없는 과거 세션의 경우 마지막 타임스탬프 기준으로 계산
    const allTimestamps: number[] = [];
    timeline.forEach(t => t.timestamp && allTimestamps.push(t.timestamp < 10000000000 ? t.timestamp * 1000 : t.timestamp));
    dataPoints.forEach(d => d.timestamp && allTimestamps.push(d.timestamp < 10000000000 ? d.timestamp * 1000 : d.timestamp));

    if (allTimestamps.length > 0) {
      const lastTs = Math.max(...allTimestamps);
      return formatDuration(Math.max(0, lastTs - startTs));
    }

    return '--';
  }, [isLive, startedAt, endedAt, timeline, dataPoints]);

  const { avgViewers, peakViewers } = useMemo(() => {
    if (summaryAvg !== null && summaryAvg !== undefined && summaryPeak !== null && summaryPeak !== undefined) {
      return { avgViewers: summaryAvg, peakViewers: summaryPeak };
    }

    const viewerSamples: number[] = [];

    timeline.forEach(t => {
      if (typeof t.viewerCount === 'number' && t.viewerCount > 0) {
        viewerSamples.push(t.viewerCount);
      }
    });

    dataPoints.forEach(d => {
      if (typeof d.viewerCount === 'number' && d.viewerCount > 0) {
        viewerSamples.push(d.viewerCount);
      }
    });

    if (currentViewers > 0) {
      viewerSamples.push(currentViewers);
    }

    if (viewerSamples.length === 0) {
      return {
        avgViewers: summaryAvg ?? currentViewers ?? 0,
        peakViewers: summaryPeak ?? currentViewers ?? 0
      };
    }

    const sum = viewerSamples.reduce((acc, v) => acc + v, 0);
    const avg = summaryAvg ?? Math.round(sum / viewerSamples.length);
    const peak = summaryPeak ?? Math.max(...viewerSamples);

    return { avgViewers: avg, peakViewers: peak };
  }, [timeline, dataPoints, currentViewers, summaryAvg, summaryPeak]);

  return (
    <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 my-6 sm:my-8">
      <div className="p-4 sm:p-5 bg-[#0c0d0f] border border-gray-800/80 rounded-2xl relative overflow-hidden flex flex-col justify-between group hover:border-[#00FFA3]/40 transition-all duration-300">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs text-gray-400 font-semibold tracking-wider flex items-center gap-1.5 truncate">
            <span className="text-sm">⏱️</span> 총 방송 시간
          </span>
          {isLive && (
            <span className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-500/10 text-red-400 border border-red-500/20 shrink-0">
              <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
              진행 중
            </span>
          )}
        </div>
        <div>
          <div className="text-xl sm:text-2xl lg:text-3xl font-black text-white font-mono tracking-tight truncate">
            {durationText}
          </div>
          <p className="text-[10px] sm:text-[11px] text-gray-400 mt-1 font-medium truncate">
            {isLive ? '시작 시각부터 누적' : '방송 세션 전체 지속 시간'}
          </p>
        </div>
      </div>

      <div className="p-4 sm:p-5 bg-[#0c0d0f] border border-gray-800/80 rounded-2xl relative overflow-hidden flex flex-col justify-between group hover:border-[#67BFFF]/40 transition-all duration-300">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs text-gray-200 font-bold tracking-wider flex items-center gap-1.5 truncate">
            <span className="text-sm">👥</span> 평균 시청자
          </span>
        </div>
        <div>
          <div className="text-xl sm:text-2xl lg:text-3xl font-black text-[#67BFFF] font-mono tracking-tight truncate">
            {avgViewers > 0 ? avgViewers.toLocaleString() : '--'}
            <span className="text-xs text-gray-400 font-sans font-normal ml-1">명</span>
          </div>
          <p className="text-[10px] sm:text-[11px] text-gray-400 mt-1 font-medium truncate">
            {isLive ? '현재 세션 시청자 평균' : '세션 집계 평균'}
          </p>
        </div>
      </div>

      <div className="p-4 sm:p-5 bg-[#0c0d0f] border border-gray-800/80 rounded-2xl relative overflow-hidden flex flex-col justify-between group hover:border-[#A78BFA]/40 transition-all duration-300">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs text-gray-200 font-bold tracking-wider flex items-center gap-1.5 truncate">
            <span className="text-sm">🚀</span> 최고 시청자
          </span>
        </div>
        <div>
          <div className="text-xl sm:text-2xl lg:text-3xl font-black text-[#A78BFA] font-mono tracking-tight truncate">
            {peakViewers > 0 ? peakViewers.toLocaleString() : '--'}
            <span className="text-xs text-gray-400 font-sans font-normal ml-1">명</span>
          </div>
          <p className="text-[10px] sm:text-[11px] text-gray-400 mt-1 font-medium truncate">
            세션 피크 동시 접속자
          </p>
        </div>
      </div>

      <div className="p-4 sm:p-5 bg-[#0c0d0f] border border-gray-800/80 rounded-2xl relative overflow-hidden flex flex-col justify-between group hover:border-[#00FFA3]/40 transition-all duration-300">
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs text-gray-200 font-bold tracking-wider flex items-center gap-1.5 truncate">
            <span className="text-sm">💬</span> 구독자 채팅 비율
          </span>
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#00FFA3]/10 text-[#00FFA3] border border-[#00FFA3]/20 shrink-0">
            {subscriberChatRate !== null ? '분석 완료' : '집계 중'}
          </span>
        </div>
        <div>
          <div className="text-xl sm:text-2xl lg:text-3xl font-black text-white font-mono tracking-tight truncate">
            {subscriberChatRate !== null ? `${subscriberChatRate.toFixed(1)}%` : '준비 중'}
          </div>
          <p className="text-[10px] sm:text-[11px] text-gray-400 mt-1 font-medium truncate">
            전체 채팅 중 팬덤 활성도
          </p>
        </div>
      </div>
    </div>
  );
};
