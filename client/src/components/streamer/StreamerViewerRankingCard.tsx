import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { type StreamItem } from '../../types/stream';

interface Props {
  streamers: StreamItem[];
  isLoading: boolean;
}

export const StreamerViewerRankingCard: React.FC<Props> = ({ streamers, isLoading }) => {
  const navigate = useNavigate();
  const [isExpanded, setIsExpanded] = useState(false);

  const displayedStreamers = isExpanded ? streamers.slice(0, 100) : streamers.slice(0, 20);

  return (
    <div className="bg-[#141416] border border-[#2A2A2C] rounded-2xl p-4 sm:p-5 flex flex-col justify-between shadow-xl hover:border-gray-700/80 transition-all">
      <div>
        {/* 헤더 */}
        <div className="flex items-center justify-between pb-3.5 mb-3 border-b border-gray-800/80">
          <div className="flex items-center gap-2">
            <span className="text-xl">📊</span>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base sm:text-lg font-black text-white tracking-tight">
                  30일 평균 시청자 순위
                </h3>
                <div className="relative group/tip flex items-center">
                  <button
                    type="button"
                    className="w-4 h-4 rounded-full border border-gray-600 flex items-center justify-center text-[10px] font-bold text-gray-400 hover:text-[#00FFA3] hover:border-[#00FFA3] transition-colors focus:outline-none"
                    aria-label="30일 평균 시청자 순위 산출 방식 안내"
                  >
                    i
                  </button>
                  <div className="absolute left-0 bottom-full mb-2 hidden group-hover/tip:block w-72 p-3 bg-[#18181c] border border-gray-700 text-xs text-gray-300 rounded-xl shadow-2xl z-50 font-normal leading-relaxed text-left backdrop-blur-md pointer-events-none break-keep">
                    <p className="font-semibold text-white mb-1">📊 산출 기준</p>
                    <p className="text-gray-300">
                      최근 30일간 방송의 길이를 반영한 <span className="text-white font-medium">시간 가중 평균 시청자 수</span>를 기준으로 순위를 산정합니다.
                    </p>
                    <p className="text-[11px] text-gray-400 pt-1 mt-1 border-t border-gray-800">
                      • 5분 미만 방송 집계 제외 / 매일 04:00 일괄 갱신
                    </p>
                  </div>
                </div>
              </div>
              <p className="text-[11px] text-gray-400 font-medium">
                체급 랭킹 • {isExpanded ? '1~100위' : 'TOP 20'}
              </p>
            </div>
          </div>
          <span className="text-xs font-mono font-bold text-[#00FFA3] px-2.5 py-1 rounded-full bg-[#00FFA3]/10 border border-[#00FFA3]/20">
            총 {streamers.length}명
          </span>
        </div>

        {/* 랭킹 리스트 */}
        {isLoading && streamers.length === 0 ? (
          <div className="py-20 text-center text-gray-400 text-xs flex flex-col items-center justify-center gap-2">
            <div className="w-6 h-6 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
            시청자 랭킹 데이터를 집계하는 중입니다...
          </div>
        ) : (
          <div className="space-y-1">
            {displayedStreamers.map((stream, idx) => {
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

                  {/* 우측: 평균 시청자 수 */}
                  <div className="text-right shrink-0 pl-2">
                    <p className="font-extrabold text-sm text-white font-mono group-hover:text-[#00FFA3] transition-colors">
                      {(stream.averageViewers ?? 0) > 0
                        ? `${(stream.averageViewers ?? 0).toLocaleString()}명`
                        : '-'}
                    </p>
                    <p className="text-[10px] text-gray-500 font-mono">
                      평균 시청자
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 더보기 / 접기 버튼 */}
      {streamers.length > 20 && (
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
