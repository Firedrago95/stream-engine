import React from 'react';

// 상대 시간 포맷터 (00:00:00)
const formatOffset = (ms: number | undefined | null) => {
  if (ms === null || ms === undefined) return "--:--:--";
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;

  if (h > 0) {
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

// 절대 시간 포맷터 (HH:mm)
const formatAbsoluteTime = (isoString: string) => {
  try {
    const date = new Date(isoString);
    return date.toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  } catch (e) {
    return "";
  }
};

// 안전한 인라인 툴팁 컴포넌트
const InfoTooltip = ({ text }: { text: string }) => (
    <div className="group relative inline-flex items-center ml-2 cursor-help align-text-bottom z-30">
    <span className="text-gray-500 hover:text-[#00FFA3] transition-colors">
      <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
      </svg>
    </span>
      <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block w-72 p-4 bg-[#1a1a1c] text-gray-200 text-[13px] rounded-xl shadow-[0_10px_30px_rgba(0,0,0,0.5)] border border-gray-700 pointer-events-none text-left whitespace-pre-line leading-relaxed">
        {text}
      </div>
    </div>
);

export const HighlightSection: React.FC<{ highlights: any[] }> = ({ highlights }) => (
    <div className="mt-12">
      <div className="flex items-center mb-6 px-2">
        <span className="text-2xl mr-2">🔥</span>
        <h3 className="text-xl font-black text-white italic uppercase tracking-tighter">주요 하이라이트</h3>
        <span className="ml-3 px-2 py-0.5 bg-gray-800 text-[#00FFA3] text-xs font-bold rounded border border-gray-700">{highlights.length}</span>

        {/* 로직 설명 툴팁 추가 */}
        <InfoTooltip text="🎬 하이라이트는 어떻게 뽑나요?&#10;&#10;치즈픽은 지난 1시간의 채팅 흐름을 분석해 상위 1%로 화력이 폭발하는 진짜 레전드 타이밍을 찾아냅니다!&#10;&#10;연달아 터지는 리액션도 뚝뚝 끊기지 않게 약 1분 30초의 매끄러운 영상으로 스마트하게 묶어드려요 ✨" />
      </div>

      <div className="grid gap-3">
        {highlights.length === 0 ? (
            <div className="p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-[#a1a1aa] font-bold italic text-sm">아직 감지된 하이라이트가 없습니다.</div>
        ) : (
            [...highlights].reverse().map((hl) => (
                <div key={hl.id} className={`flex items-center p-6 bg-[#1a1a1c] border ${hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800'} rounded-2xl hover:border-gray-600 transition-colors`}>
                  <div className="flex-1">
                    <span className="text-[10px] text-[#a1a1aa] font-black uppercase mb-1 block tracking-widest text-opacity-70">발생 시점 (영상 기준)</span>
                    <div className="flex items-end gap-1.5">

                      {/* VOD 아이콘 및 스타일 개선 */}
                      <span className="text-white font-mono text-xl font-black leading-none flex items-center gap-1.5">
                        <span className="text-gray-500 text-lg">🎬</span> {formatOffset(hl.startTimeOffset)}
                      </span>

                      <span className="text-[#52525b] text-[11px] font-bold mb-[2px] ml-1">
                        (실제 {formatAbsoluteTime(hl.startTime)})
                      </span>

                      <span className="text-gray-600 font-black text-lg px-1.5 leading-none self-center">~</span>

                      {hl.status === 'ONGOING' ? (
                          <span className="text-red-500 animate-pulse text-sm font-black italic ml-1">진행 중 🔴</span>
                      ) : (
                          <span className="text-gray-300 font-mono text-xl font-bold leading-none">
                            {formatOffset(hl.endTimeOffset)}
                          </span>
                      )}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-[#a1a1aa] font-black uppercase mb-1 block tracking-widest text-opacity-70">최고 화력</span>
                    <span className="text-[#00FFA3] font-black text-2xl">{hl.peakFirepower}<span className="text-xs text-[#a1a1aa] ml-1">msg/s</span></span>
                  </div>
                </div>
            ))
        )}
      </div>
    </div>
);
