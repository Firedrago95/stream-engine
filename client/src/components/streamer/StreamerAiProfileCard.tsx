import React from 'react';

export interface AiProfileData {
  summaryLine: string;
  broadcastingStyle: string;
  coreGames: string[];
  fandomTraits: string;
  popularMemes: string[];
  analyzedSessionCount: number;
  updatedAt: string;
}

interface StreamerAiProfileCardProps {
  aiProfile?: AiProfileData | null;
  streamerName: string;
}

export const StreamerAiProfileCard: React.FC<StreamerAiProfileCardProps> = ({
  aiProfile,
  streamerName,
}) => {
  if (!aiProfile) {
    return null;
  }

  return (
    <section className="relative overflow-hidden rounded-2xl border border-[#00FFA3]/20 bg-gradient-to-br from-[#15171c] via-[#121316] to-[#0e0f12] p-5 sm:p-6 shadow-xl">
      {/* 배경 은은한 글로우 액센트 */}
      <div className="pointer-events-none absolute -right-16 -top-16 h-48 w-48 rounded-full bg-[#00FFA3]/5 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-[#67BFFF]/5 blur-3xl" />

      {/* 상단 뱃지 & 헤더 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#2A2A2C] pb-3 mb-4">
        <div className="flex items-center gap-2">
          <span className="flex h-6 items-center gap-1.5 rounded-full bg-[#00FFA3]/15 px-2.5 text-[11px] font-bold text-[#00FFA3] border border-[#00FFA3]/30">
            <span className="h-1.5 w-1.5 rounded-full bg-[#00FFA3] animate-pulse" />
            치즈픽 AI 분석 리포트
          </span>
          <span className="text-xs text-gray-400 font-medium hidden sm:inline">
            {streamerName} 스트리머 데이터 요약
          </span>
        </div>
        <span className="text-[11px] text-gray-500 font-mono">
          최근 30일 누적 분석
        </span>
      </div>

      {/* 한 줄 핵심 요약 */}
      <div className="mb-5">
        <h3 className="text-base sm:text-lg font-bold text-white tracking-tight flex items-start gap-2">
          <span className="text-[#00FFA3] text-lg select-none">💬</span>
          <span className="leading-snug text-gray-100">
            "{aiProfile.summaryLine}"
          </span>
        </h3>
      </div>

      {/* 3단 그리드 카드 (방송 스타일 / 주력 콘텐츠 / 팬덤 & 대표 밈) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5 sm:gap-4">
        {/* 1. 방송 스타일 */}
        <div className="rounded-xl border border-[#252528] bg-[#16171b]/80 p-3.5 sm:p-4 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-1.5 text-xs font-bold text-gray-300 mb-2">
              <span>🎙️</span>
              <span>방송 스타일</span>
            </div>
            <p className="text-xs sm:text-[13px] text-gray-300 leading-relaxed font-normal">
              {aiProfile.broadcastingStyle}
            </p>
          </div>
        </div>

        {/* 2. 주력 콘텐츠 & 게임 */}
        <div className="rounded-xl border border-[#252528] bg-[#16171b]/80 p-3.5 sm:p-4 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-1.5 text-xs font-bold text-gray-300 mb-2">
              <span>🎮</span>
              <span>주력 콘텐츠</span>
            </div>
            <div className="flex flex-wrap gap-1.5 mt-1">
              {aiProfile.coreGames && aiProfile.coreGames.length > 0 ? (
                aiProfile.coreGames.map((game, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center rounded-lg bg-[#202228] px-2 py-1 text-[11px] font-medium text-gray-200 border border-gray-700/60"
                  >
                    {game}
                  </span>
                ))
              ) : (
                <span className="text-xs text-gray-500">종합 게임 & 소통</span>
              )}
            </div>
          </div>
        </div>

        {/* 3. 팬덤 특성 & 대표 밈 */}
        <div className="rounded-xl border border-[#252528] bg-[#16171b]/80 p-3.5 sm:p-4 flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-1.5 text-xs font-bold text-gray-300 mb-2">
              <span>💬</span>
              <span>팬덤 분위기 & 대표 밈</span>
            </div>
            <p className="text-xs sm:text-[13px] text-gray-300 leading-relaxed font-normal mb-2.5">
              {aiProfile.fandomTraits}
            </p>
            {aiProfile.popularMemes && aiProfile.popularMemes.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1 border-t border-gray-800">
                {aiProfile.popularMemes.map((meme, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center rounded-md bg-[#00FFA3]/10 px-2 py-0.5 text-[11px] font-medium text-[#00FFA3] border border-[#00FFA3]/20"
                  >
                    #{meme}
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};
