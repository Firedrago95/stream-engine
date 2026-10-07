import React from 'react';
import { useStreamers } from '../../hooks/useStreamers';
import { StreamerSearchBar } from './StreamerSearchBar';
import { StreamerViewerRankingCard } from './StreamerViewerRankingCard';
import { StreamerFollowerRankingCard } from './StreamerFollowerRankingCard';

export const StreamerStatsPage: React.FC = () => {
  // 전체 리더보드 스트리머 목록 (30일 평균 시청자 순위용)
  const { streamers, isLoading } = useStreamers('', 30000);

  return (
    <div className="space-y-8 pb-16">
      {/* 상단 헤더 & 검색창 */}
      <div className="p-8 sm:p-12 bg-gradient-to-b from-[#18181c] to-[#121214] border border-[#2A2A2C] rounded-3xl text-center relative overflow-visible shadow-2xl">
        <div className="max-w-2xl mx-auto space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold bg-[#00FFA3]/15 text-[#00FFA3] border border-[#00FFA3]/30">
            📊 치지직 스트리머 리포트
          </div>
          <h1 className="text-3xl sm:text-4xl font-black text-white tracking-tight italic">
            STREAMER <span className="text-[#00FFA3]">ANALYTICS</span>
          </h1>
          <p className="text-sm text-gray-300 font-medium">
            방송 중이 아니어도 활동명을 검색해 시청자, 팔로워, 방송 이력을 확인하세요.
          </p>

          {/* 독립 플로팅 검색바 */}
          <StreamerSearchBar />
        </div>
      </div>

      {/* 2분할 랭킹 대시보드 (좌측: 30일 평균 시청자 순위 / 우측: 팔로워 랭킹) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* 좌측: 30일 평균 시청자 체급 순위 (TOP 20 -> 100위 확장) */}
        <StreamerViewerRankingCard streamers={streamers} isLoading={isLoading} />

        {/* 우측: 팔로워 랭킹 (기본: 주간 급상승, 토글: 누적 팔로워, TOP 20 -> 100위 확장) */}
        <StreamerFollowerRankingCard />
      </div>
    </div>
  );
};
