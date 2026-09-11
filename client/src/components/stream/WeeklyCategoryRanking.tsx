import React from 'react';
import Tooltip from '../Tooltip.jsx';

export interface WeeklyCategory {
  rank: number;
  categoryName: string;
  accumulatedViewHours: string;
  change: 'up' | 'down' | 'same' | 'new';
  changeValue?: number;
  icon?: string;
}

const DEFAULT_CATEGORIES: WeeklyCategory[] = [
  { rank: 1, categoryName: '메이플스토리', accumulatedViewHours: '약 6.6만 시간', change: 'up', changeValue: 2, icon: '🍁' },
  { rank: 2, categoryName: '마인크래프트', accumulatedViewHours: '약 5.8만 시간', change: 'up', changeValue: 3, icon: '⛏️' },
  { rank: 3, categoryName: '종합 게임', accumulatedViewHours: '약 4.2만 시간', change: 'same', icon: '🎮' },
  { rank: 4, categoryName: '리그 오브 레전드', accumulatedViewHours: '약 4.2만 시간', change: 'down', changeValue: 3, icon: '⚔️' },
  { rank: 5, categoryName: '오버워치', accumulatedViewHours: '약 3.0만 시간', change: 'new', icon: '🎯' },
  { rank: 6, categoryName: '이터널 리턴', accumulatedViewHours: '약 2.5만 시간', change: 'down', changeValue: 1, icon: '🧪' },
];

export const WeeklyCategoryRanking: React.FC = () => {
  const getChangeBadge = (item: WeeklyCategory) => {
    switch (item.change) {
      case 'up':
        return (
          <span className="flex items-center text-[10px] font-bold text-emerald-400">
            ▲ {item.changeValue || 1}
          </span>
        );
      case 'down':
        return (
          <span className="flex items-center text-[10px] font-bold text-rose-400">
            ▼ {item.changeValue || 1}
          </span>
        );
      case 'new':
        return (
          <span className="px-1 py-0.2 bg-[#00FFA3]/20 text-[#00FFA3] text-[9px] font-extrabold rounded">
            NEW
          </span>
        );
      default:
        return <span className="text-[10px] font-bold text-gray-500">-</span>;
    }
  };

  const getRankBadgeClass = (rank: number) => {
    if (rank === 1) return 'bg-[#00FFA3] text-black font-black shadow-sm';
    if (rank === 2) return 'bg-slate-300 text-black font-black';
    if (rank === 3) return 'bg-amber-600 text-white font-black';
    return 'bg-gray-800 text-gray-300 font-bold';
  };

  return (
    <div className="mb-8 p-4 sm:p-5 bg-[#141416] border border-[#26262b] rounded-2xl shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <span className="text-xl">🏆</span>
          <h3 className="text-lg sm:text-xl font-bold text-gray-100 italic tracking-tight">
            주간 인기 카테고리
          </h3>
          <Tooltip bg="dark" position="right" size="md" className="mt-0.5">
            <div className="text-xs text-gray-200 font-medium leading-relaxed p-1.5 space-y-1">
              <div className="font-bold text-[#00FFA3] mb-1">📊 집계 방식 안내</div>
              <div>• 치즈픽에 수집된 상위 라이브 방송 대상</div>
              <div>• 최근 7일간 15초 단위 <span className="text-white font-semibold">동시 시청자 수 타임라인</span>을 기반으로 누적 시청 시간을 계산한 순위</div>
            </div>
          </Tooltip>
        </div>

        <div className="flex items-center gap-2">
          <span className="px-2.5 py-1 bg-[#1c1b1d] border border-[#26262b] text-[11px] font-medium text-[#00FFA3] rounded-full">
            매주 월요일 갱신
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {DEFAULT_CATEGORIES.map((item) => {
          const isFirst = item.rank === 1;

          return (
            <div
              key={item.categoryName}
              className={`p-3 rounded-xl transition-all duration-200 flex flex-col justify-between border ${
                isFirst
                  ? 'bg-[#18181a] border-[#00FFA3]/50 shadow-[0_0_15px_-3px_rgba(0,255,163,0.15)]'
                  : 'bg-[#1a1a1c] border-gray-800'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <span
                  className={`w-5 h-5 flex items-center justify-center rounded text-xs ${getRankBadgeClass(
                    item.rank
                  )}`}
                >
                  {item.rank}
                </span>
                {getChangeBadge(item)}
              </div>

              <div className="my-1">
                <div className="text-sm font-bold text-white truncate flex items-center gap-1.5" title={item.categoryName}>
                  <span>{item.icon}</span>
                  <span className="truncate">{item.categoryName}</span>
                </div>
              </div>

              <div className="mt-2 pt-2 border-t border-gray-800/80 flex items-center justify-between text-[11px]">
                <span className="text-gray-400">누적</span>
                <span className="font-mono text-gray-200 font-semibold">{item.accumulatedViewHours}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
