import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useFollowerRankings } from '../../hooks/useFollowerRankings';
import { type FollowerRankingType } from '../../types/followerRanking';

export const StreamerFollowerRankingCard: React.FC = () => {
  const navigate = useNavigate();
  const [rankingType, setRankingType] = useState<FollowerRankingType>('GROWTH');
  const [isExpanded, setIsExpanded] = useState(false);

  const { rankings, isLoading } = useFollowerRankings(rankingType, 100);

  const displayedRankings = isExpanded ? rankings.slice(0, 100) : rankings.slice(0, 20);

  return (
    <div className="bg-[#141416] border border-[#2A2A2C] rounded-2xl p-4 sm:p-5 flex flex-col justify-between shadow-xl hover:border-gray-700/80 transition-all">
      <div>
        {/* 헤더 & 토글 스위치 */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3.5 mb-3 border-b border-gray-800/80">
          <div className="flex items-center gap-2">
            <span className="text-xl">
              {rankingType === 'GROWTH' ? '🚀' : '🏆'}
            </span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base sm:text-lg font-black text-white tracking-tight">
                  {rankingType === 'GROWTH' ? '주간 팔로워 급상승' : '누적 팔로워 순위'}
                </h3>
                <div className="relative group/tip flex items-center">
                  <button
                    type="button"
                    className="w-4 h-4 rounded-full border border-gray-600 flex items-center justify-center text-[10px] font-bold text-gray-400 hover:text-[#00FFA3] hover:border-[#00FFA3] transition-colors focus:outline-none"
                    aria-label="팔로워 랭킹 산출 방식 안내"
                  >
                    i
                  </button>
                  <div className="absolute left-0 bottom-full mb-2 hidden group-hover/tip:block w-72 p-3 bg-[#18181c] border border-gray-700 text-xs text-gray-300 rounded-xl shadow-2xl z-50 font-normal leading-relaxed text-left backdrop-blur-md pointer-events-none break-keep">
                    <p className="font-semibold text-white mb-1">
                      {rankingType === 'GROWTH' ? '🚀 주간 급상승 기준' : '🏆 누적 팔로워 기준'}
                    </p>
                    <p className="text-gray-300">
                      {rankingType === 'GROWTH'
                        ? '최근 7일간 일일 스냅샷을 기반으로 순수 팔로워 증가량이 가장 많은 스트리머를 산정합니다.'
                        : '치지직 전체 스트리머 중 현재 보유한 총 팔로워 수를 기준으로 산정합니다.'}
                    </p>
                    <p className="text-[11px] text-gray-400 pt-1 mt-1 border-t border-gray-800">
                      • 매일 자정 스냅샷 기준 갱신
                    </p>
                  </div>
                </div>
              </div>
              <p className="text-[11px] text-gray-400 font-medium">
                {rankingType === 'GROWTH' ? '최근 7일 증가량' : '전체 팬덤 규모'} •{' '}
                {isExpanded ? '1~100위' : 'TOP 20'}
              </p>
            </div>
          </div>

          {/* 탭 토글 버튼 */}
          <div className="inline-flex p-1 bg-[#0e0e10] border border-gray-800 rounded-xl shrink-0 self-start sm:self-center">
            <button
              type="button"
              onClick={() => {
                setRankingType('GROWTH');
                setIsExpanded(false);
              }}
              className={`px-3 py-1 text-xs font-bold rounded-lg transition-all ${
                rankingType === 'GROWTH'
                  ? 'bg-[#00FFA3] text-black shadow-sm font-extrabold'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              🚀 주간 급상승
            </button>
            <button
              type="button"
              onClick={() => {
                setRankingType('TOTAL');
                setIsExpanded(false);
              }}
              className={`px-3 py-1 text-xs font-bold rounded-lg transition-all ${
                rankingType === 'TOTAL'
                  ? 'bg-[#00FFA3] text-black shadow-sm font-extrabold'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              🏆 누적 팔로워
            </button>
          </div>
        </div>

        {/* 랭킹 리스트 */}
        {isLoading && rankings.length === 0 ? (
          <div className="py-20 text-center text-gray-400 text-xs flex flex-col items-center justify-center gap-2">
            <div className="w-6 h-6 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
            팔로워 랭킹 데이터를 집계하는 중입니다...
          </div>
        ) : rankings.length === 0 ? (
          <div className="py-16 text-center text-gray-400 text-xs">
            집계된 팔로워 랭킹 데이터가 없습니다.
          </div>
        ) : (
          <div className="space-y-1">
            {displayedRankings.map((stream, idx) => {
              const isStreaming = stream.status === 'LIVE' || stream.status === 'ANALYZING';
              return (
                <div
                  key={stream.streamId}
                  onClick={() => navigate(`/streamers/${stream.streamId}`)}
                  className="flex items-center justify-between p-2 sm:px-3 rounded-xl hover:bg-[#1a2c22]/60 hover:shadow-[inset_0_0_0_1px_rgba(0,255,163,0.3)] cursor-pointer transition-all group"
                >
                  {/* 좌측: 순위 + 프로필 + 이름 */}
                  <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
                    <span
                      className={`shrink-0 w-6 h-6 rounded-md flex items-center justify-center text-xs font-black font-mono ${
                        idx === 0
                          ? 'bg-[#00FFA3] text-black shadow-sm'
                          : idx === 1
                          ? 'bg-slate-300 text-black'
                          : idx === 2
                          ? 'bg-amber-600 text-white'
                          : 'bg-[#1e1e24] text-gray-400'
                      }`}
                    >
                      {idx + 1}
                    </span>

                    <div className="relative shrink-0">
                      <img
                        src={stream.profileImageUrl || '/cheese-pick-logo.png'}
                        alt={stream.streamerName}
                        className="w-8 h-8 rounded-full object-cover border border-gray-800 group-hover:border-[#00FFA3]/40 transition-colors"
                      />
                      {isStreaming && (
                        <span className="absolute bottom-0 right-0 w-2 h-2 bg-red-500 border border-[#141416] rounded-full" />
                      )}
                    </div>

                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <p className="font-bold text-sm text-white group-hover:text-[#00FFA3] transition-colors truncate">
                          {stream.streamerName}
                        </p>
                        {isStreaming && (
                          <span className="shrink-0 text-[9px] font-extrabold px-1.5 py-0.2 rounded-full bg-red-500/15 text-red-400 border border-red-500/30">
                            LIVE
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-gray-400 truncate max-w-[130px] sm:max-w-[180px]">
                        {stream.categoryName || '기타'}
                      </p>
                    </div>
                  </div>

                  {/* 우측: 팔로워 지표 */}
                  <div className="text-right shrink-0 pl-2">
                    {rankingType === 'GROWTH' ? (
                      <>
                        <p className="font-extrabold text-sm text-[#00FFA3] font-mono flex items-center justify-end gap-1">
                          <span>+{(stream.weeklyGrowth ?? 0).toLocaleString()}명</span>
                          <span className="text-[11px]">🔺</span>
                        </p>
                        <p className="text-[10px] text-gray-500 font-mono">
                          총 {(stream.followerCount ?? 0).toLocaleString()}명
                        </p>
                      </>
                    ) : (
                      <>
                        <p className="font-extrabold text-sm text-white font-mono group-hover:text-[#00FFA3] transition-colors">
                          {(stream.followerCount ?? 0).toLocaleString()}명
                        </p>
                        <p className="text-[10px] text-gray-500 font-mono">
                          누적 팔로워
                        </p>
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 더보기 / 접기 버튼 */}
      {rankings.length > 20 && (
        <button
          type="button"
          onClick={() => setIsExpanded(!isExpanded)}
          className="mt-4 w-full py-2.5 px-4 rounded-xl bg-[#1a1a1e] hover:bg-[#222228] text-xs font-bold text-gray-300 hover:text-[#00FFA3] border border-gray-800 hover:border-gray-700 transition-all flex items-center justify-center gap-1.5"
        >
          {isExpanded ? (
            <>
              <span>▲ TOP 20으로 접기</span>
            </>
          ) : (
            <>
              <span>더보기 (100위까지)</span>
              <span className="text-[#00FFA3]">→</span>
            </>
          )}
        </button>
      )}
    </div>
  );
};
