import React from 'react';
export const AnalysisTabs: React.FC<{ availableDates: string[]; selected: string; onSelect: (tab: string) => void; }> = ({ availableDates, selected, onSelect }) => (
    <div className="mb-8 flex gap-2">
      {availableDates.map((tab) => (
          <button key={tab} onClick={() => onSelect(tab)} className={`px-6 py-2.5 rounded-full text-sm font-bold transition-all border ${selected === tab ? 'bg-[#00FFA3] border-[#00FFA3] text-gray-900 shadow-[0_0_15px_rgba(0,255,163,0.3)]' : 'bg-gray-800 border-gray-700 text-gray-400 hover:text-white'}`}>
            {tab === "realtime" ? "⚡ 실시간 분석" : tab.slice(5)}
          </button>
      ))}
    </div>
);
