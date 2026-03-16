// src/components/stream/dashboard/HighlightSection.tsx

import React from 'react';

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
    return new Date(isoString).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });
  } catch (e) { return ""; }
};

export const HighlightSection: React.FC<{ highlights: any[] }> = ({ highlights }) => (
    <div className="mt-12">
      <div className="flex items-center mb-6 px-2">
        <span className="text-2xl mr-2">🔥</span>
        <h3 className="text-xl font-black text-white italic tracking-tighter">주요 하이라이트</h3>
      </div>

      <div className="grid gap-3">
        {highlights.length === 0 ? (
            <div className="p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-500 font-bold italic">진행된 하이라이트가 없습니다.</div>
        ) : (
            [...highlights].reverse().map((hl) => (
                <div key={hl.id} className={`flex items-center p-6 bg-[#1a1a1c] border ${hl.status === 'ONGOING' ? 'border-[#00FFA3] bg-[#00FFA3]/5' : 'border-gray-800'} rounded-2xl`}>
                  <div className="flex-1">
                    <span className="text-[10px] text-gray-400 font-black mb-1 block tracking-widest">발생 시점 (영상 기준)</span>
                    <div className="flex items-center gap-2 h-8">
                
                <span className="text-white font-mono text-xl font-black flex items-center gap-1.5">
                  <span className="text-gray-500 text-lg">🎬</span> {formatOffset(hl.startTimeOffset)}
                </span>
                      <span className="text-gray-400 text-[11px] font-bold">({formatAbsoluteTime(hl.startTime)})</span>

                      <span className="text-gray-400 font-black text-lg px-2">~</span>

                      {hl.status === 'ONGOING' ? (
                          <div className="flex items-center gap-1.5 px-2 py-1 bg-red-500/10 rounded-lg border border-red-500/20">
                            <span className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse" />
                            <span className="text-red-500 text-[11px] font-black">분석 중</span>
                          </div>
                      ) : (
                          <div className="flex items-center gap-2">
                            <span className="text-white font-mono text-xl font-black">{formatOffset(hl.endTimeOffset)}</span>
                            <span className="text-gray-400 text-[11px] font-bold">({formatAbsoluteTime(hl.endTime)})</span>
                          </div>
                      )}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-gray-400 font-black mb-1 block tracking-widest">최고 화력</span>
                    <span className="text-[#00FFA3] font-black text-2xl">{hl.peakFirepower}<span className="text-xs text-gray-500 ml-1">msg/s</span></span>
                  </div>
                </div>
            ))
        )}
      </div>
    </div>
);
