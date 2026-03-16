// src/components/stream/dashboard/AnalysisTabs.tsx

import React from 'react';

const InfoTooltip = ({ text }: { text: string }) => (
    <div className="group relative inline-flex items-center ml-1.5 cursor-help z-30">
    <span className="text-gray-500 hover:text-[#00FFA3] transition-colors">
      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
      </svg>
    </span>
      <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block w-64 p-3 bg-[#1a1a1c] text-gray-200 text-xs rounded-xl shadow-2xl border border-gray-700 pointer-events-none text-left whitespace-pre-line leading-relaxed">
        {text}
      </div>
    </div>
);

export const AnalysisTabs: React.FC<{
  availableDates: string[];
  selected: string;
  onSelect: (tab: string) => void;
}> = ({ availableDates, selected, onSelect }) => (
    <div className="mb-8 mt-4 relative z-10 w-full overflow-hidden">

      {/* 헤더 부분 */}
      <div className="flex items-center mb-3 px-1">
        <span className="text-sm font-black text-gray-400 uppercase tracking-widest">날짜 선택</span>
        <InfoTooltip text="자정을 넘긴 방송도 흐름이 끊기지 않게!\n매일 새벽 6시를 기준으로 하루치 방송을 묶어서 보여드려요. 📅" />
      </div>

      {/* flex-nowrap으로 줄바꿈 방지, overflow-x-auto로 가로 스크롤 활성화 */}
      <div
          className="flex flex-nowrap gap-3 overflow-x-auto pb-2 items-center w-full"
          style={{ scrollbarWidth: 'none', msOverflowStyle: 'none', WebkitOverflowScrolling: 'touch' }}
      >
        {availableDates.map((tab) => (
            <button
                key={tab}
                onClick={() => onSelect(tab)}
                // shrink-0 옵션으로 버튼이 찌그러지지 않고 원래 크기를 유지하게 만듦
                className={`shrink-0 px-6 py-2.5 rounded-full text-sm font-bold transition-all border whitespace-nowrap
            ${selected === tab
                    ? 'bg-[#00FFA3] border-[#00FFA3] text-black shadow-[0_0_15px_rgba(0,255,163,0.4)]'
                    : 'bg-[#1a1a1c] border-gray-700 text-gray-300 hover:border-gray-500 hover:bg-gray-800'}`}
            >
              {tab === "realtime" ? "⚡ 실시간 분석" : tab.slice(5)}
            </button>
        ))}
      </div>

      {/* 지저분한 스크롤바를 숨기는 커스텀 스타일 (웹킷 브라우저용) */}
      <style dangerouslySetInnerHTML={{__html: `
      div::-webkit-scrollbar { display: none; }
    `}} />
    </div>
);
