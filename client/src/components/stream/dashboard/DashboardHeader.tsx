import React from 'react';
export const DashboardHeader: React.FC<{ onBack: () => void }> = ({ onBack }) => (
    <div className="py-8 flex items-center gap-3">
      <button onClick={onBack} className="p-2 bg-[#1a1a1c] border border-gray-700 rounded-lg text-gray-300 hover:text-[#00FFA3] transition-colors">
        <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
      </button>
      <h2 className="text-xl font-bold text-white tracking-tight">분석 대시보드</h2>
    </div>
);
