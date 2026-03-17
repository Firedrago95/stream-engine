import React, { useState } from 'react';

const InfoTooltip = ({ text }: { text: string }) => (
    <div className="group relative inline-flex items-center ml-1.5 cursor-help z-50">
    <span className="text-gray-300 hover:text-[#00FFA3] transition-colors">
      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
      </svg>
    </span>
      <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block w-56 p-3 bg-[#1a1a1c] text-gray-200 text-xs rounded-xl shadow-2xl border border-gray-600 pointer-events-none text-left whitespace-pre-line leading-relaxed">
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
      ? `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
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

export const HighlightSection: React.FC<HighlightSectionProps> = ({ highlights, selectedTab }) => {
  const [showAll, setShowAll] = useState(false);
  const isRealtime = selectedTab === 'realtime';

  // [데이터 정렬 및 필터링 로직]
  const processedHighlights = React.useMemo(() => {
    let list = [...highlights];

    if (isRealtime) {
      // 1. 실시간: 최고 화력순으로 정렬하여 상위 6개만 노출
      return list
      .sort((a, b) => (b.peakFirepower || 0) - (a.peakFirepower || 0))
      .slice(0, 6);
    } else {
      // 2. 과거 세션: VOD 인덱스 역할을 위해 시간순(오래된 순) 정렬
      list.sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());

      // "더보기" 상태가 아니면 6개만 노출
      return showAll ? list : list.slice(0, 6);
    }
  }, [highlights, isRealtime, showAll]);

  return (
      <div className="mt-12">
        <div className="flex items-center justify-between mb-6 px-2">
          <div className="flex items-center gap-2">
            <span className="text-2xl">🔥</span>
            <h3 className="text-xl font-black text-white italic tracking-tighter">
              {isRealtime ? "실시간 핫 타임 (Top 6)" : "방송 하이라이트"}
            </h3>
            <span className="px-2.5 py-0.5 bg-[#00FFA3]/20 border border-[#00FFA3]/30 text-[#00FFA3] rounded-lg text-sm font-black">
            {isRealtime ? processedHighlights.length : highlights.length}
          </span>
            <InfoTooltip text={isRealtime ? "현재 방송에서 화력이 가장 높았던 상위 6개 구간입니다." : "과거 방송의 전체 하이라이트 목록입니다."} />
          </div>

          {!isRealtime && (
              <span className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">
            정렬: 시간순
          </span>
          )}
        </div>

        {/* 그리드 레이아웃: 한 줄에 2개씩 배치 (md:grid-cols-2) */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {processedHighlights.length === 0 ? (
              <div className="col-span-full p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-500 font-bold italic">
                데이터가 없습니다.
              </div>
          ) : (
              processedHighlights.map((hl) => (
                  <div
                      key={hl.id}
                      className={`flex items-center p-5 bg-[#1a1a1c] border ${hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800'} rounded-2xl hover:border-gray-700 transition-colors shadow-sm`}
                  >
                    <div className="flex-1">
                      <span className="text-[9px] text-gray-400 font-black mb-1 block tracking-widest uppercase">발생 시점</span>
                      <div className="flex items-center gap-2 h-7">
                  <span className="text-white font-mono text-lg font-black shrink-0">
                    <span className="text-gray-500 text-base mr-1">🎬</span>
                    {formatOffset(hl.startTimeOffset)}
                  </span>
                        <span className="text-gray-400 text-[10px] font-bold">({formatAbsoluteTime(hl.startTime)})</span>
                      </div>
                    </div>

                    <div className="text-right border-l border-gray-800/50 pl-4 ml-2">
                      <span className="text-[9px] text-gray-400 font-black mb-1 block tracking-widest uppercase">최고 화력</span>
                      <span className="text-[#00FFA3] font-black text-xl leading-none">
                  {hl.peakFirepower}
                        <span className="text-[10px] text-gray-500 ml-1 font-bold">msg/s</span>
                </span>
                    </div>
                  </div>
              ))
          )}
        </div>

        {/* 더보기 버튼: 과거 세션이면서 데이터가 6개보다 많을 때만 표시 */}
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
