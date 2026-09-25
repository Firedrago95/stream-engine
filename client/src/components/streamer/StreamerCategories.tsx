import React from 'react';

export interface CategoryData {
  categoryName: string;
  totalDurationSeconds: number;
  percentage: number;
  averageViewers: number;
}

interface StreamerCategoriesProps {
  categories: CategoryData[];
}

const formatHours = (seconds: number) => {
  const hours = Math.round((seconds / 3600) * 10) / 10;
  return `${hours}시간`;
};

export const StreamerCategories: React.FC<StreamerCategoriesProps> = ({ categories }) => {
  if (!categories || categories.length === 0) {
    return (
      <div className="p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl text-center text-gray-200 font-medium text-sm">
        최근 30일간의 카테고리 분석 데이터가 없습니다.
      </div>
    );
  }

  return (
    <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
      <div className="flex items-center justify-between mb-5">
        <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
          <span>🎮</span> 주력 카테고리 분석 (최근 30일)
        </h3>
        <span className="text-xs text-gray-200 font-medium">누적 플레이 시간 기준 Top 5</span>
      </div>

      <div className="space-y-4">
        {categories.map((cat, idx) => (
          <div key={cat.categoryName} className="group">
            <div className="flex items-center justify-between text-xs mb-1.5 font-medium">
              <div className="flex items-center gap-2">
                <span className="w-5 h-5 rounded flex items-center justify-center bg-gray-800 text-gray-100 font-bold text-[10px]">
                  {idx + 1}
                </span>
                <span className="text-gray-100 font-bold text-sm truncate max-w-[180px] sm:max-w-xs">
                  {cat.categoryName}
                </span>
              </div>
              <div className="flex items-center gap-4 text-right">
                <span className="text-gray-100 font-mono">
                  평균 <span className="text-[#67BFFF] font-bold">{cat.averageViewers.toLocaleString()}명</span>
                </span>
                <span className="text-gray-200 font-mono font-bold w-16">
                  {formatHours(cat.totalDurationSeconds)}
                </span>
                <span className="text-[#00FFA3] font-mono font-black w-12 text-right">
                  {cat.percentage}%
                </span>
              </div>
            </div>

            <div className="w-full h-2.5 bg-[#1e1e24] rounded-full overflow-hidden">
              <div
                className="h-full rounded-full bg-gradient-to-r from-[#00D084] to-[#00FFA3] transition-all duration-500 group-hover:brightness-110"
                style={{ width: `${Math.min(100, Math.max(2, cat.percentage))}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
