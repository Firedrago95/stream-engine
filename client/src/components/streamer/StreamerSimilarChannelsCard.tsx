import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStreamerSimilarity } from '../../hooks/useStreamerSimilarity';

interface StreamerSimilarChannelsCardProps {
  channelId: string;
}

const RANK_BADGES: Record<number, { bg: string; text: string; border: string; label: string }> = {
  1: {
    bg: 'bg-amber-400/15',
    text: 'text-amber-300',
    border: 'border-amber-400/40',
    label: '1위',
  },
  2: {
    bg: 'bg-slate-300/15',
    text: 'text-slate-200',
    border: 'border-slate-300/40',
    label: '2위',
  },
  3: {
    bg: 'bg-amber-700/15',
    text: 'text-amber-400',
    border: 'border-amber-700/40',
    label: '3위',
  },
};

export const StreamerSimilarChannelsCard: React.FC<StreamerSimilarChannelsCardProps> = ({ channelId }) => {
  const navigate = useNavigate();
  const { data, isLoading, error } = useStreamerSimilarity(channelId);
  const [showTooltip, setShowTooltip] = useState(false);

  if (error || (!isLoading && !data)) {
    return null;
  }

  return (
    <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl relative">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-5">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
              <span>👥</span> 시청자 성향 유사 채널 TOP 3
            </h3>
            <div className="relative inline-flex items-center">
              <button
                type="button"
                onMouseEnter={() => setShowTooltip(true)}
                onMouseLeave={() => setShowTooltip(false)}
                onClick={() => setShowTooltip((prev) => !prev)}
                className="w-4 h-4 rounded-full bg-gray-800 hover:bg-gray-700 text-gray-200 hover:text-white text-[10px] font-bold inline-flex items-center justify-center transition-colors cursor-help"
                aria-label="유사 채널 산출 기준 안내"
              >
                i
              </button>

              {showTooltip && (
                <div className="absolute left-6 top-1/2 -translate-y-1/2 z-30 w-72 p-3 bg-[#1C1C1F] border border-[#333336] rounded-xl shadow-2xl text-xs text-gray-200 leading-relaxed pointer-events-none animate-in fade-in zoom-in-95 duration-150">
                  <p className="font-bold text-white mb-1">🔍 유사도 산출 기준</p>
                  <p>
                    최근 30일간 해당 채널 채팅에 참여한 고유 시청자 풀과 타 채널 시청자 풀 간의 교집합 비율(자카드 유사도)을 계산하여 팬덤 성향이 가장 겹치는 방송을 추천합니다.
                  </p>
                  <div className="mt-2 pt-2 border-t border-gray-800 text-[11px] text-gray-200 font-medium">
                    • 최소 100명 이상의 채팅 표본 필요<br />
                    • 최고 유사도 3.0% 미만 시 독립 팬덤으로 분류
                  </div>
                </div>
              )}
            </div>
          </div>
          <p className="text-xs text-gray-100 font-medium mt-1">이 채널의 시청자가 자주 찾는 방송</p>
        </div>

        {data?.calculatedDate && (
          <span className="text-[11px] text-gray-200 font-medium font-mono self-start sm:self-auto">
            {data.calculatedDate} 집계 기준
          </span>
        )}
      </div>

      {isLoading && (
        <div className="space-y-3 py-2 animate-pulse">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 bg-[#1a1a1e] rounded-xl border border-gray-800/60" />
          ))}
        </div>
      )}

      {!isLoading && data?.status === 'INSUFFICIENT_DATA' && (
        <div className="p-6 bg-[#1a1a1e]/60 border border-gray-800/80 rounded-xl text-center">
          <div className="text-2xl mb-2">📊</div>
          <p className="text-sm font-bold text-white mb-1">
            최근 30일간 채팅 활동 데이터가 부족합니다
          </p>
          <p className="text-xs text-gray-100 max-w-md mx-auto leading-relaxed">
            유의미한 시청자 성향 분석을 위해 최근 30일간 최소 100명 이상의 고유 시청자 채팅 표본이 필요합니다.
          </p>
        </div>
      )}

      {!isLoading && data?.status === 'INDEPENDENT_FANDOM' && (
        <div className="p-6 bg-gradient-to-r from-[#181824] to-[#161622] border border-[#2d2d42] rounded-xl text-center">
          <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-purple-500/15 border border-purple-500/30 text-purple-300 text-[11px] font-bold mb-2.5">
            <span>👑</span> 독립 고유 팬덤
          </div>
          <p className="text-sm font-bold text-white mb-1">
            독립적인 고유 팬덤을 보유한 채널입니다
          </p>
          <p className="text-xs text-gray-100 max-w-md mx-auto leading-relaxed">
            타 방송과 시청자층이 겹치지 않고 본 채널을 집중적으로 소비하는 충성도 높은 고유 팬덤 형태를 띱니다. (최고 유사도 3.0% 미만)
          </p>
        </div>
      )}

      {!isLoading && data?.status === 'NORMAL' && data.items.length > 0 && (
        <div className="space-y-3">
          {data.items.map((item) => {
            const badge = RANK_BADGES[item.rank] || {
              bg: 'bg-gray-800',
              text: 'text-gray-100',
              border: 'border-gray-700',
              label: `${item.rank}위`,
            };

            return (
              <div
                key={item.channelId}
                onClick={() => navigate(`/streamers/${item.channelId}`)}
                className="group p-3.5 sm:p-4 bg-[#18181b] hover:bg-[#1f1f24] border border-[#26262a] hover:border-[#383842] rounded-xl transition-all cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <span
                    className={`w-10 h-7 rounded-lg ${badge.bg} ${badge.text} border ${badge.border} flex items-center justify-center font-black text-xs font-mono shrink-0`}
                  >
                    {badge.label}
                  </span>

                  <img
                    src={item.profileImageUrl || '/cheese-pick-logo.png'}
                    alt={item.streamerName}
                    className="w-11 h-11 rounded-full object-cover border border-gray-700 shrink-0 group-hover:ring-2 group-hover:ring-[#00FFA3]/60 transition-all"
                  />

                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white group-hover:text-[#00FFA3] transition-colors truncate">
                        {item.streamerName}
                      </span>
                      {item.primaryCategory && (
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-gray-800 text-gray-200 border border-gray-700 shrink-0">
                          {item.primaryCategory}
                        </span>
                      )}
                    </div>
                    {item.commonChatterCount != null && (
                      <p className="text-[11px] text-gray-200 font-mono mt-0.5">
                        공통 채팅자 약 <strong className="text-white font-bold">{item.commonChatterCount.toLocaleString()}</strong>명
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between sm:justify-end gap-4 shrink-0 pl-13 sm:pl-0">
                  <div className="w-28 sm:w-36">
                    <div className="flex items-center justify-between text-[11px] mb-1">
                      <span className="text-gray-200 font-medium">팬덤 일치율</span>
                      <span className="text-[#00FFA3] font-mono font-black text-sm">
                        {item.similarityPercent}%
                      </span>
                    </div>
                    <div className="w-full h-2 bg-[#121214] rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-[#00D084] to-[#00FFA3] transition-all duration-500 group-hover:brightness-110"
                        style={{ width: `${Math.min(100, Math.max(5, item.similarityPercent * 2.5))}%` }}
                      />
                    </div>
                  </div>

                  <svg
                    className="w-4 h-4 text-gray-200 group-hover:text-white group-hover:translate-x-0.5 transition-all shrink-0 hidden sm:block"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                  </svg>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
