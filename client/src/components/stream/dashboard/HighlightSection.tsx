import React, {useState} from 'react';

const InfoTooltip = ({text}: { text: string }) => (
    <div className="group relative inline-flex items-center ml-1.5 cursor-help z-50">
    <span className="text-gray-300 hover:text-[#00FFA3] transition-colors">
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
}

export const HighlightSection: React.FC<HighlightSectionProps> = ({highlights, selectedTab}) => {
  const [showAll, setShowAll] = useState(false);
  const isRealtime = selectedTab === 'realtime';

  const processedHighlights = React.useMemo(() => {
    let list = [...highlights];
    list.sort((a, b) => b.peakFirepower - a.peakFirepower);
    if (isRealtime) {
      return list;
    } else {
      return showAll ? list : list.slice(0, 6);
    }
  }, [highlights, isRealtime, showAll]);

  return (
      <div className="mt-12">
        <div className="flex items-center justify-between mb-6 px-2">
          <div className="flex items-center gap-2">
            <span className="text-2xl">🔥</span>
            <h3 className="text-xl font-black text-white italic tracking-tighter">
              {isRealtime ? "실시간 하이라이트 (Top 6)" : "방송 하이라이트"}
            </h3>
            <span className="px-2.5 py-0.5 bg-[#00FFA3]/20 border border-[#00FFA3]/30 text-[#00FFA3] rounded-lg text-sm font-black">
            {isRealtime ? processedHighlights.length : highlights.length}
          </span>
            <InfoTooltip
                text={isRealtime ? "현재 방송에서 화력이 가장 높았던 상위 6개 구간입니다." : "과거 방송의 전체 하이라이트 목록입니다."}/>
          </div>

          {!isRealtime && (
              <span className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">
            정렬: 시간순
          </span>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processedHighlights.length === 0 ? (
              <div className="col-span-full p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-500 font-bold italic">
                데이터가 없습니다.
              </div>
          ) : (
              processedHighlights.map((hl, index) => {
                const getRankStyles = (idx: number) => {
                  switch (idx) {
                    case 0: // 1등 Gold
                      return {
                        card: "border-yellow-500/30 bg-gradient-to-br from-yellow-500/10 to-transparent shadow-lg hover:border-yellow-500/50",
                        badge: "border-yellow-500 bg-yellow-500/20 text-yellow-500 shadow-[0_0_15px_rgba(234,179,8,0.3)]",
                        peak: "text-yellow-500",
                        label: "text-yellow-500/70"
                      };
                    case 1: // 2등 Silver
                      return {
                        card: "border-blue-100/30 bg-[#1a1a1c] hover:border-blue-100/50",
                        badge: "border-white bg-blue-100/20 text-white shadow-[0_0_10px_rgba(255,255,255,0.4)]",
                        peak: "text-white",
                        label: "text-blue-100/60"
                      };
                    case 2: // 3등 Bronze (채도 보정)
                      return {
                        card: "border-orange-600/20 bg-[#1a1a1c] hover:border-orange-600/40",
                        badge: "border-orange-600 bg-orange-600/10 text-orange-600",
                        peak: "text-orange-600",
                        label: "text-gray-500"
                      };
                    default: // 4등 이하
                      return {
                        card: hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800 bg-[#1a1a1c]',
                        badge: "border-gray-800 text-gray-500 bg-gray-900/50",
                        peak: "text-[#00FFA3]",
                        label: "text-gray-400"
                      };
                  }
                };

                const style = getRankStyles(index);

                return (
                    <div
                        key={hl.id}
                        className={`flex items-center p-5 border rounded-2xl transition-all shadow-sm ${style.card}`}
                    >
                      <div
                          className={`mr-4 flex-shrink-0 flex items-center justify-center w-8 h-8 rounded-full border-2 font-black text-xs italic ${style.badge}`}>
                        {index + 1}
                      </div>

                      <div className="flex-1">
                      <span
                          className={`text-[9px] font-black mb-1 block tracking-widest uppercase ${style.label}`}>발생 시점</span>
                        <div className="flex items-center gap-2 h-7">
                        <span className="text-white font-mono text-lg font-black shrink-0">
                          <span className="text-gray-500 text-base mr-1">🎬</span>
                          {formatOffset(hl.startTimeOffset)}
                          <span className="mx-1.5 text-gray-600 font-normal">~</span>
                          {formatOffset(hl.endTimeOffset)}
                        </span>
                          <span
                              className="text-gray-400 text-[10px] font-bold">({formatAbsoluteTime(hl.startTime)})</span>
                        </div>
                      </div>

                      <div className="text-right border-l border-gray-800/50 pl-4 ml-2">
                      <span className="text-[9px] text-gray-400 font-black mb-1 block tracking-widest uppercase">최고 화력</span>
                        <span className={`font-black text-xl leading-none ${style.peak}`}>
                        {hl.peakFirepower}
                          <span className="text-[10px] text-gray-500 ml-1 font-bold">msg/s</span>
                      </span>
                      </div>
                    </div>
                );
              })
          )}
        </div>

        {!isRealtime && highlights.length > 6 && (
            <button
                onClick={() => setShowAll(!showAll)}
                className="w-full mt-6 py-4 bg-[#1a1a1c] border border-gray-800 rounded-2xl text-gray-400 text-sm font-bold hover:bg-gray-800 hover:text-white transition-all shadow-lg group"
            >
              {showAll ? (
                  "▲ 하이라이트 접기"
              ) : (
                  <div className="flex items-center justify-center gap-2">
                    <span>▼ 전체 하이라이트 보기</span>
                    <span className="bg-gray-800 px-2 py-0.5 rounded text-[11px] group-hover:bg-gray-700">
                {highlights.length - 6}개 더보기
              </span>
                  </div>
              )}
            </button>
        )}
      </div>
  );
};
