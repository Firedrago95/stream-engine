import React, {useState} from 'react';

const InfoTooltip = ({text}: { text: string }) => (
    <div className="group relative inline-flex items-center ml-1.5 cursor-help z-50">
    <span className="text-gray-100 hover:text-[#00FFA3] transition-colors">
      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z"
              clipRule="evenodd"/>
      </svg>
    </span>
      <div
          className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block w-56 p-3 bg-[#1a1a1c] text-gray-200 text-xs rounded-xl shadow-2xl border border-gray-600 pointer-events-none text-left whitespace-pre-line leading-relaxed">
        {text}
      </div>
    </div>
);

const formatOffset = (ms: number | undefined | null) => {
  if (ms === null || ms === undefined) return "--:--:--";
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;

  return h > 0
      ? `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
      : `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

const formatAbsoluteTime = (isoString: string) => {
  try {
    return new Date(isoString).toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  } catch (e) {
    return "";
  }
};

interface HighlightSectionProps {
  highlights: any[];
  selectedTab: string;
  startedAt?: string;
  endedAt?: string | null;
}

export const HighlightSection: React.FC<HighlightSectionProps> = ({
  highlights,
  selectedTab,
  startedAt,
  endedAt,
}) => {
  const [viewMode, setViewMode] = useState<'top6' | 'recommended' | 'all'>('top6');
  const [sortOrder, setSortOrder] = useState<'firepower' | 'time'>('firepower');
  const isRealtime = selectedTab === 'realtime';

  const durationHours = React.useMemo(() => {
    if (!startedAt) return 1;
    const start = new Date(startedAt).getTime();
    if (isNaN(start)) return 1;

    const end = endedAt ? new Date(endedAt).getTime() : Date.now();
    if (isNaN(end) || end <= start) return 1;

    return Math.max(1, (end - start) / (1000 * 60 * 60));
  }, [startedAt, endedAt]);

  const recommendedLimit = React.useMemo(() => {
    return Math.max(6, Math.round(durationHours * 4));
  }, [durationHours]);

  const firepowerRankMap = React.useMemo(() => {
    const sorted = [...highlights].sort((a, b) => b.peakFirepower - a.peakFirepower);
    const map = new Map<string | number, number>();
    sorted.forEach((hl, index) => {
      map.set(hl.id, index + 1);
    });
    return map;
  }, [highlights]);

  const processedHighlights = React.useMemo(() => {
    const byFirepower = [...highlights].sort((a, b) => b.peakFirepower - a.peakFirepower);

    let selected = byFirepower;
    if (viewMode === 'top6') {
      selected = byFirepower.slice(0, 6);
    } else if (viewMode === 'recommended') {
      selected = byFirepower.slice(0, recommendedLimit);
    }

    if (sortOrder === 'time') {
      return [...selected].sort((a, b) => {
        const timeA = a.startTimeOffset ?? (a.startTime ? new Date(a.startTime).getTime() : 0);
        const timeB = b.startTimeOffset ?? (b.startTime ? new Date(b.startTime).getTime() : 0);
        return timeA - timeB;
      });
    }

    return selected;
  }, [highlights, viewMode, recommendedLimit, sortOrder]);

  const titleText = React.useMemo(() => {
    const prefix = isRealtime ? "실시간 하이라이트" : "방송 하이라이트";
    if (highlights.length <= 6) return prefix;
    if (viewMode === 'top6') return `${prefix} (Top 6)`;
    if (viewMode === 'recommended') {
      const count = Math.min(recommendedLimit, highlights.length);
      return `${prefix} (추천 ${count}선)`;
    }
    return `${prefix} (전체 ${highlights.length}개)`;
  }, [isRealtime, highlights.length, viewMode, recommendedLimit]);

  const countBadgeText = React.useMemo(() => {
    if (highlights.length <= 6) return `${highlights.length}`;
    if (viewMode === 'top6') return `6 / ${highlights.length}`;
    if (viewMode === 'recommended') {
      const count = Math.min(recommendedLimit, highlights.length);
      return `${count} / ${highlights.length}`;
    }
    return `${highlights.length}`;
  }, [highlights.length, viewMode, recommendedLimit]);

  return (
      <div className="mt-12">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-6 px-2">
          <div className="flex items-center gap-2">
            <span className="text-2xl">🔥</span>
            <h3 className="text-xl font-black text-white italic tracking-tighter">
              {titleText}
            </h3>
            <span className="px-2.5 py-0.5 bg-[#00FFA3]/20 border border-[#00FFA3]/30 text-[#00FFA3] rounded-lg text-sm font-black">
              {countBadgeText}
            </span>
            <InfoTooltip
                text={
                  isRealtime
                    ? "현재 진행 중인 방송의 하이라이트입니다.\n- Top 6: 최상위 화력 순간\n- 추천 뷰: 방송 시간 기반(시간당 4개) 엄선\n- 정렬 토글로 타임라인순/화력순 전환 가능"
                    : "방송 하이라이트 목록입니다.\n- Top 6: 최상위 화력 순간\n- 추천 뷰: 방송 시간 기반(시간당 4개) 엄선\n- 정렬 토글로 타임라인순/화력순 전환 가능"
                }
            />
          </div>

          {highlights.length > 0 && (
            <div className="flex items-center bg-[#1a1a1c] p-1 rounded-xl border border-gray-800 text-xs font-bold self-start sm:self-auto">
              <button
                onClick={() => setSortOrder('firepower')}
                className={`px-3 py-1.5 rounded-lg transition-all flex items-center gap-1 ${
                  sortOrder === 'firepower'
                    ? 'bg-gray-800 text-[#00FFA3] shadow-sm'
                    : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                <span>🔥</span> 화력순
              </button>
              <button
                onClick={() => setSortOrder('time')}
                className={`px-3 py-1.5 rounded-lg transition-all flex items-center gap-1 ${
                  sortOrder === 'time'
                    ? 'bg-gray-800 text-[#00FFA3] shadow-sm'
                    : 'text-gray-400 hover:text-gray-200'
                }`}
              >
                <span>⏱️</span> 타임라인순
              </button>
            </div>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processedHighlights.length === 0 ? (
              <div className="col-span-full p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-200 font-bold italic">
                데이터가 없습니다.
              </div>
          ) : (
              processedHighlights.map((hl) => {
                const rank = firepowerRankMap.get(hl.id) || 1;
                const getRankStyles = (r: number) => {
                  switch (r) {
                    case 1: // 1등 Gold
                      return {
                        card: "border-yellow-500/30 bg-gradient-to-br from-yellow-500/10 to-transparent shadow-lg hover:border-yellow-500/50",
                        badge: "border-yellow-500 bg-yellow-500/20 text-yellow-500 shadow-[0_0_15px_rgba(234,179,8,0.3)]",
                        peak: "text-yellow-500",
                        label: "text-yellow-500/70"
                      };
                    case 2: // 2등 Silver
                      return {
                        card: "border-blue-100/30 bg-[#1a1a1c] hover:border-blue-100/50",
                        badge: "border-white bg-blue-100/20 text-white shadow-[0_0_10px_rgba(255,255,255,0.4)]",
                        peak: "text-white",
                        label: "text-blue-100/60"
                      };
                    case 3: // 3등 Bronze
                      return {
                        card: "border-orange-600/20 bg-[#1a1a1c] hover:border-orange-600/40",
                        badge: "border-orange-600 bg-orange-600/10 text-orange-600",
                        peak: "text-orange-600",
                        label: "text-gray-100"
                      };
                    default: // 4등 이하
                      return {
                        card: hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800 bg-[#1a1a1c]',
                        badge: "border-gray-700 text-gray-100 bg-gray-900/50",
                        peak: "text-[#00FFA3]",
                        label: "text-gray-100"
                      };
                  }
                };

                const style = getRankStyles(rank);

                return (
                    <div
                        key={hl.id}
                        className={`flex items-center p-3.5 sm:p-5 border rounded-2xl transition-all shadow-sm ${style.card}`}
                    >
                      <div
                          className={`mr-3 sm:mr-4 flex-shrink-0 flex items-center justify-center w-7 h-7 sm:w-8 sm:h-8 rounded-full border-2 font-black text-xs italic ${style.badge}`}
                          title={`화력 순위 ${rank}위`}
                      >
                        {rank}
                      </div>

                      <div className="flex-1 min-w-0">
                        <span
                            className={`text-[9px] font-black mb-0.5 sm:mb-1 block tracking-widest uppercase ${style.label}`}>발생 시점</span>
                        <div className="flex flex-wrap sm:flex-nowrap items-center gap-1 sm:gap-2">
                          <span className="text-white font-mono text-sm sm:text-base lg:text-lg font-black shrink-0">
                            <span className="text-gray-100 text-xs sm:text-base mr-1">🎬</span>
                            {formatOffset(hl.startTimeOffset)}
                            <span className="mx-1 text-white font-normal">~</span>
                            {formatOffset(hl.endTimeOffset)}
                          </span>
                          <span
                              className="text-gray-100 text-[10px] font-bold">({formatAbsoluteTime(hl.startTime)})</span>
                        </div>
                      </div>

                      <div className="text-right border-l border-gray-800/50 pl-3 sm:pl-4 ml-2 shrink-0">
                        <span className="text-[9px] text-gray-100 font-black mb-0.5 sm:mb-1 block tracking-widest uppercase">최고 화력</span>
                        <span className={`font-black text-lg sm:text-xl leading-none ${style.peak}`}>
                          {hl.peakFirepower}
                          <span className="text-[10px] text-gray-100 ml-1 font-bold">msg/s</span>
                        </span>
                      </div>
                    </div>
                );
              })
          )}
        </div>

        {highlights.length > 6 && (
          <div className="mt-6 flex flex-col items-center gap-3">
            {viewMode === 'top6' && (
              <button
                onClick={() => setViewMode(highlights.length <= recommendedLimit ? 'all' : 'recommended')}
                className="w-full py-4 bg-[#1a1a1c] border border-gray-800 rounded-2xl text-gray-100 text-sm font-bold hover:bg-gray-800 hover:text-white transition-all shadow-lg group"
              >
                <div className="flex items-center justify-center gap-2">
                  <span>▼ 추천 하이라이트 보기</span>
                  <span className="bg-gray-800 px-2.5 py-0.5 rounded text-[11px] group-hover:bg-gray-700 text-[#00FFA3]">
                    {Math.min(recommendedLimit, highlights.length) - 6}개 더보기
                    {highlights.length > recommendedLimit && ` (약 ${durationHours.toFixed(1)}시간 기준)`}
                  </span>
                </div>
              </button>
            )}

            {viewMode === 'recommended' && (
              <div className="w-full flex flex-col items-center gap-2.5">
                <button
                  onClick={() => setViewMode('top6')}
                  className="w-full py-4 bg-[#1a1a1c] border border-gray-800 rounded-2xl text-gray-100 text-sm font-bold hover:bg-gray-800 hover:text-white transition-all shadow-lg"
                >
                  ▲ 기본(Top 6)으로 접기
                </button>

                {highlights.length > recommendedLimit && (
                  <div className="flex items-center justify-center gap-2 text-xs text-gray-400 mt-1">
                    <span>시간당 4개 기준으로 선별되었습니다.</span>
                    <button
                      onClick={() => setViewMode('all')}
                      className="text-[#00FFA3] hover:underline font-bold"
                    >
                      전체 {highlights.length}개 모두 펼치기
                    </button>
                  </div>
                )}
              </div>
            )}

            {viewMode === 'all' && (
              <div className="w-full flex flex-col items-center gap-2.5">
                <button
                  onClick={() => setViewMode(highlights.length > recommendedLimit ? 'recommended' : 'top6')}
                  className="w-full py-4 bg-[#1a1a1c] border border-gray-800 rounded-2xl text-gray-100 text-sm font-bold hover:bg-gray-800 hover:text-white transition-all shadow-lg"
                >
                  {highlights.length > recommendedLimit
                    ? `▲ 추천 하이라이트로 접기 (${Math.min(recommendedLimit, highlights.length)}개)`
                    : "▲ 기본(Top 6)으로 접기"}
                </button>
                {highlights.length > recommendedLimit && (
                  <button
                    onClick={() => setViewMode('top6')}
                    className="text-xs text-gray-400 hover:text-gray-200 transition-colors"
                  >
                    기본(Top 6)으로 바로 접기
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>
  );
};

