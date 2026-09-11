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
  { rank: 1, categoryName: 'League of Legends', accumulatedViewHours: '128.4만 시간', change: 'same', icon: '⚔️' },
  { rank: 2, categoryName: '로스트아크', accumulatedViewHours: '89.2만 시간', change: 'up', changeValue: 1, icon: '🛡️' },
  { rank: 3, categoryName: '발로란트', accumulatedViewHours: '74.6만 시간', change: 'down', changeValue: 1, icon: '🎯' },
  { rank: 4, categoryName: '소통/토크', accumulatedViewHours: '65.1만 시간', change: 'same', icon: '💬' },
  { rank: 5, categoryName: '메이플스토리', accumulatedViewHours: '42.8만 시간', change: 'up', changeValue: 2, icon: '🍁' },
  { rank: 6, categoryName: '치지직 종합게임', accumulatedViewHours: '38.5만 시간', change: 'new', icon: '🎮' },
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
            <div className="text-xs text-gray-200 font-medium leading-relaxed p-1">
              치지직 상위 인기 방송을 기준으로 지난주(월~일) 누적 시청 시간을 집계한 순위입니다.
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
