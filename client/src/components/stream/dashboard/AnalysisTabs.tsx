import React from 'react';

export interface DashboardSessionTab {
  sessionId: string;
  label: string;
  isLive?: boolean;
  liveTitle?: string;
  categoryName?: string;
  viewers?: number;
  startedAt?: string;
}

interface AnalysisTabsProps {
  availableSessions: DashboardSessionTab[];
  selected: string;
  onSelect: (tab: string) => void;
}

export const AnalysisTabs: React.FC<AnalysisTabsProps> = ({ availableSessions, selected, onSelect }) => {
  return (
    <div className="flex gap-3 overflow-x-auto pb-6 mb-2 scrollbar-hide snap-x">
      {availableSessions.map((tab) => {
        const isSelected = selected === tab.sessionId;
        const isLive = !!tab.isLive;

        return (
          <button
            key={tab.sessionId}
            onClick={() => onSelect(tab.sessionId)}
            className={`
              snap-start px-5 py-3 rounded-xl font-bold text-sm whitespace-nowrap transition-all duration-200 border shrink-0 flex items-center gap-1.5
              ${isSelected
                ? (isLive
                  ? 'bg-[#00FFA3] text-black border-[#00FFA3] shadow-[0_0_15px_rgba(0,255,163,0.3)]'
                  : 'bg-white text-black border-white shadow-[0_0_15px_rgba(255,255,255,0.2)]')
                : (isLive
                  ? 'bg-[#1a1a1c] text-[#00FFA3] border-[#00FFA3]/30 hover:border-[#00FFA3]'
                  : 'bg-[#1a1a1c] text-gray-400 border-gray-800 hover:text-white hover:border-gray-600')
              }
            `}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
};
