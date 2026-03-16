import React from 'react';

// 안전한 인라인 툴팁 컴포넌트
const InfoTooltip = ({ text }: { text: string }) => (
    <div className="group relative inline-flex items-center ml-1.5 cursor-help z-30">
    <span className="text-gray-500 hover:text-[#00FFA3] transition-colors">
      <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
      </svg>
    </span>
        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block w-64 p-3 bg-[#1a1a1c] text-gray-200 text-xs rounded-xl shadow-[0_10px_30px_rgba(0,0,0,0.5)] border border-gray-700 pointer-events-none text-left whitespace-pre-line leading-relaxed">
            {text}
        </div>
    </div>
);

export const AnalysisTabs: React.FC<{
    availableDates: string[];
    selected: string;
    onSelect: (tab: string) => void;
}> = ({ availableDates, selected, onSelect }) => (
    <div className="mb-8">
        <div className="flex items-center mb-3 px-1">
            <span className="text-sm font-black text-gray-400 uppercase tracking-widest">Date</span>
            <InfoTooltip text="자정을 넘긴 방송도 한눈에 볼 수 있도록, 매일 새벽 6시를 기준으로 하루 치 방송을 묶어서 보여드려요! 📅" />
        </div>

        {/* 가로 스크롤 칩 */}
        <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide" style={{ msOverflowStyle: 'none', scrollbarWidth: 'none' }}>
            {availableDates.map((tab) => (
                <button
                    key={tab}
                    onClick={() => onSelect(tab)}
                    className={`shrink-0 px-5 py-2.5 rounded-full text-sm font-bold transition-all border flex items-center gap-2
                  ${selected === tab
                        ? 'bg-[#00FFA3]/10 border-[#00FFA3] text-[#00FFA3] shadow-[0_0_15px_rgba(0,255,163,0.15)]'
                        : 'bg-[#1a1a1c] border-gray-800 text-gray-400 hover:text-gray-200 hover:border-gray-600'}`}
                >
                    {tab === "realtime" ? "⚡ 실시간 분석" : tab.slice(5)}
                </button>
            ))}
        </div>
    </div>
);
