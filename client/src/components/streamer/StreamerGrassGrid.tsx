import React, { useState } from 'react';

export interface GrassTileData {
  date: string;
  level: 'LEVEL_0' | 'LEVEL_1' | 'LEVEL_2' | 'LEVEL_3' | 'LEVEL_4';
  durationSeconds: number;
  avgViewers: number;
  peakViewers: number;
  representativeTitle?: string;
  dominantCategory?: string;
}

interface StreamerGrassGridProps {
  tiles: GrassTileData[];
  currentStreak: number;
  totalDurationSeconds: number;
  totalBroadcastDays: number;
  selectedDays: number;
  onDaysChange: (days: number) => void;
}

const formatDuration = (seconds: number) => {
  if (!seconds || seconds <= 0) return '0시간';
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}시간 ${minutes}분`;
  }
  return `${minutes}분`;
};

const getLevelColor = (level: string) => {
  switch (level) {
    case 'LEVEL_4':
      return 'bg-[#00FFA3] shadow-[0_0_8px_rgba(0,255,163,0.5)]';
    case 'LEVEL_3':
      return 'bg-[#00D084]';
    case 'LEVEL_2':
      return 'bg-[#008F5A]';
    case 'LEVEL_1':
      return 'bg-[#005234]';
    default:
      return 'bg-[#1e1e24] hover:bg-[#282830]';
  }
};

export const StreamerGrassGrid: React.FC<StreamerGrassGridProps> = ({
  tiles,
  currentStreak,
  totalDurationSeconds,
  totalBroadcastDays,
  selectedDays,
  onDaysChange,
}) => {
  const [hoveredTile, setHoveredTile] = useState<GrassTileData | null>(null);
  const [tooltipPos, setTooltipPos] = useState({ x: 0, y: 0 });

  const handleMouseEnter = (tile: GrassTileData, e: React.MouseEvent) => {
    setHoveredTile(tile);
    const rect = e.currentTarget.getBoundingClientRect();
    setTooltipPos({
      x: rect.left + rect.width / 2,
      y: rect.top - 8,
    });
  };

  const handleMouseLeave = () => {
    setHoveredTile(null);
  };

  return (
    <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl relative">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div>
          <div className="flex items-center gap-3">
            <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
              <span>🌿</span> 방송 활동 잔디
            </h3>
            {currentStreak > 0 && (
              <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-black bg-[#00FFA3]/15 text-[#00FFA3] border border-[#00FFA3]/30 animate-pulse">
                🔥 {currentStreak}일 연속 방송 중!
              </span>
            )}
          </div>
          <p className="text-xs text-gray-400 mt-1">
            최근 {selectedDays}일간 총 {totalBroadcastDays}일 방송 ({formatDuration(totalDurationSeconds)})
          </p>
        </div>

        <div className="flex items-center bg-[#0e0e10] border border-[#2A2A2C] rounded-lg p-1 self-end sm:self-auto">
          {[90, 180, 365].map((days) => (
            <button
              key={days}
              onClick={() => onDaysChange(days)}
              className={`px-3 py-1 text-xs font-bold rounded-md transition-all ${
                selectedDays === days
                  ? 'bg-[#1e1e24] text-[#00FFA3] shadow-sm'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              {days === 365 ? '1년' : `${days}일`}
            </button>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto no-scrollbar pb-2">
        <div className="inline-flex gap-1.5 min-w-full">
          {tiles.map((tile, idx) => (
            <div
              key={tile.date || idx}
              onMouseEnter={(e) => handleMouseEnter(tile, e)}
              onMouseLeave={handleMouseLeave}
              className={`w-3.5 h-10 sm:w-4 sm:h-12 rounded-sm cursor-pointer transition-all duration-150 transform hover:scale-110 ${getLevelColor(
                tile.level
              )}`}
            />
          ))}
        </div>
      </div>

      <div className="flex items-center justify-between mt-4 text-[11px] text-gray-500 pt-3 border-t border-gray-800/60">
        <span>00:00 자정 기준 분할 집계</span>
        <div className="flex items-center gap-1.5">
          <span>Less</span>
          <div className="w-2.5 h-2.5 rounded-xs bg-[#1e1e24]" />
          <div className="w-2.5 h-2.5 rounded-xs bg-[#005234]" />
          <div className="w-2.5 h-2.5 rounded-xs bg-[#008F5A]" />
          <div className="w-2.5 h-2.5 rounded-xs bg-[#00D084]" />
          <div className="w-2.5 h-2.5 rounded-xs bg-[#00FFA3]" />
          <span>More</span>
        </div>
      </div>

      {hoveredTile && (
        <div
          style={{
            position: 'fixed',
            left: `${tooltipPos.x}px`,
            top: `${tooltipPos.y}px`,
            transform: 'translate(-50%, -100%)',
            pointerEvents: 'none',
            zIndex: 9999,
          }}
          className="bg-[#1a1a1c] border border-gray-700 shadow-2xl rounded-xl p-3 text-xs w-64 backdrop-blur-md"
        >
          <div className="flex items-center justify-between pb-1.5 mb-1.5 border-b border-gray-800">
            <span className="font-bold text-white">{hoveredTile.date}</span>
            <span className="text-[#00FFA3] font-mono font-bold">
              {formatDuration(hoveredTile.durationSeconds)}
            </span>
          </div>

          {hoveredTile.durationSeconds > 0 ? (
            <div className="space-y-1">
              {hoveredTile.representativeTitle && (
                <p className="text-gray-200 font-medium truncate">
                  📝 {hoveredTile.representativeTitle}
                </p>
              )}
              {hoveredTile.dominantCategory && (
                <p className="text-gray-400 truncate">
                  🎮 카테고리: <span className="text-gray-300">{hoveredTile.dominantCategory}</span>
                </p>
              )}
              <div className="flex items-center justify-between pt-1 text-[11px] text-gray-400 font-mono">
                <span>평균 {hoveredTile.avgViewers.toLocaleString()}명</span>
                <span>최고 {hoveredTile.peakViewers.toLocaleString()}명</span>
              </div>
            </div>
          ) : (
            <p className="text-gray-500 italic">방송 기록 없음 (휴방)</p>
          )}
        </div>
      )}
    </div>
  );
};
