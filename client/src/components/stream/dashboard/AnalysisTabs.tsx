import React from 'react';

interface AnalysisTabsProps {
  availableSessions: { sessionId: string; label: string }[];
  selected: string;
  onSelect: (tab: string) => void;
}

export const AnalysisTabs: React.FC<AnalysisTabsProps> = ({ availableSessions, selected, onSelect }) => {
  return (
      <div className="flex gap-3 overflow-x-auto pb-6 mb-2 scrollbar-hide snap-x">
        {availableSessions.map((tab) => {
          // 실시간 탭인지, 일반 과거 방송 탭인지 식별
          const isRealtime = tab.sessionId === "realtime";
          const isSelected = selected === tab.sessionId;

          return (
              <button
                  key={tab.sessionId}
                  onClick={() => onSelect(tab.sessionId)}
                  className={`
              snap-start px-5 py-3 rounded-xl font-bold text-sm whitespace-nowrap transition-all duration-200 border shrink-0
              ${isSelected
                      ? (isRealtime
                          // 선택된 탭 + 실시간: 강렬한 네온 그린
                          ? 'bg-[#00FFA3] text-black border-[#00FFA3] shadow-[0_0_15px_rgba(0,255,163,0.3)]'
                          // 선택된 탭 + 과거방송: 깔끔한 화이트/실버
                          : 'bg-white text-black border-white shadow-[0_0_15px_rgba(255,255,255,0.2)]')
                      : (isRealtime
                          // 비활성 탭 + 실시간: 어두운 네온 그린 테두리
                          ? 'bg-[#1a1a1c] text-[#00FFA3] border-[#00FFA3]/30 hover:border-[#00FFA3]'
                          // 비활성 탭 + 과거방송: 어두운 회색
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
