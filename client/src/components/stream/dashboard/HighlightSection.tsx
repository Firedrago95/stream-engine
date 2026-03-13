import React from 'react';
export const HighlightSection: React.FC<{ highlights: any[]; formatTime: (ts: any) => string; }> = ({ highlights, formatTime }) => (
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
                    <span className="text-[10px] text-[#a1a1aa] font-black uppercase mb-1 block tracking-widest">시간대</span>
                    <div className="flex items-center gap-3">
                      <span className="text-white font-mono text-xl font-black">{formatTime(hl.startTime)}</span>
                      <span className="text-white font-black text-xl px-1">~</span>
                      {hl.status === 'ONGOING' ? <span className="text-red-500 animate-pulse text-sm font-black italic">진행 중 🔴</span> : <span className="text-white font-mono text-xl font-black">{formatTime(hl.endTime)}</span>}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-[#a1a1aa] font-black uppercase mb-1 block tracking-widest">최고 화력</span>
                    <span className="text-[#00FFA3] font-black text-2xl">{hl.peakFirepower}<span className="text-xs text-[#a1a1aa] ml-1">msg/s</span></span>
                  </div>
                </div>
            ))
        )}
      </div>
    </div>
);
