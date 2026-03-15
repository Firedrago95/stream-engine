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

// 절대 시간 포맷터 (HH:mm) - 가독성을 위해 초 단위는 제거
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

export const HighlightSection: React.FC<{ highlights: any[] }> = ({ highlights }) => (
    <div className="mt-12">
      <div className="flex items-center gap-2 mb-6 px-2">
        <span className="text-2xl">🔥</span>
        <h3 className="text-xl font-black text-white italic uppercase tracking-tighter">주요 하이라이트</h3>
        <span className="ml-2 px-2 py-0.5 bg-gray-800 text-[#00FFA3] text-xs font-bold rounded border border-gray-700">{highlights.length}</span>
      </div>
      <div className="grid gap-3">
        {highlights.length === 0 ? (
            <div className="p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-[#a1a1aa] font-bold italic text-sm">아직 감지된 하이라이트가 없습니다.</div>
        ) : (
            [...highlights].reverse().map((hl) => (
                <div key={hl.id} className={`flex items-center p-6 bg-[#1a1a1c] border ${hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800'} rounded-2xl`}>
                  <div className="flex-1">
                    <span className="text-[10px] text-[#a1a1aa] font-black uppercase mb-1 block tracking-widest text-opacity-70">발생 시점 (방송 기준)</span>
                    <div className="flex items-end gap-1.5">
                      {/* 메인: 상대 시간 (Offset) */}
                      <span className="text-white font-mono text-xl font-black leading-none">
                        {formatOffset(hl.startTimeOffset)}
                      </span>

                      {/* 서브: 절대 시간 (시작 지점에만 작게 표시) */}
                      <span className="text-[#52525b] text-[11px] font-bold mb-[2px] ml-0.5">
                        ({formatAbsoluteTime(hl.startTime)})
                      </span>

                      <span className="text-white font-black text-lg px-1 leading-none self-center">~</span>

                      {hl.status === 'ONGOING' ? (
                          <span className="text-red-500 animate-pulse text-sm font-black italic ml-1">진행 중 🔴</span>
                      ) : (
                          <span className="text-white font-mono text-xl font-black leading-none">
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
