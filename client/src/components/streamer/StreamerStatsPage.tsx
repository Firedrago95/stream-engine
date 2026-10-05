import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStreamers } from '../../hooks/useStreamers';

export const StreamerStatsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const isSearching = searchTerm.trim().length > 0;

  // 전체 리더보드 스트리머 목록 (실시간 방송 현황 배너 전용, 검색어와 무관하게 고정 유지)
  const { streamers: defaultStreamers, isLoading: isDefaultLoading } = useStreamers('', 30000);

  // 검색어 입력 시 스트리머 검색 목록
  const { streamers: searchStreamers, isLoading: isSearchLoading } = useStreamers(
    searchTerm.trim(),
    30000,
    isSearching
  );

  const streams = isSearching ? searchStreamers : defaultStreamers;
  const isLoading = isSearching ? isSearchLoading : isDefaultLoading;

  const totalLiveCount = useMemo(() => {
    if (!defaultStreamers) return 0;
    return defaultStreamers.filter((s) => s.status === 'LIVE' || s.status === 'ANALYZING').length;
  }, [defaultStreamers]);

  const handleRowClick = (streamId: string) => {
    navigate(`/streamers/${streamId}`);
  };

  return (
    <div className="space-y-8 pb-16">
      <div className="p-8 sm:p-12 bg-gradient-to-b from-[#18181c] to-[#121214] border border-[#2A2A2C] rounded-3xl text-center relative overflow-hidden shadow-2xl">
        <div className="max-w-2xl mx-auto space-y-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold bg-[#00FFA3]/15 text-[#00FFA3] border border-[#00FFA3]/30">
            📊 치지직 스트리머 리포트
          </div>
          <h1 className="text-3xl sm:text-4xl font-black text-white tracking-tight italic">
            STREAMER <span className="text-[#00FFA3]">ANALYTICS</span>
          </h1>
          <p className="text-sm text-gray-200 font-medium">
            방송 중이 아니어도 활동명을 검색해 시청자, 팔로워, 방송 이력을 확인하세요.
          </p>

          <div className="relative pt-2">
            <input
              type="text"
              placeholder="스트리머 검색 (오프라인 포함)"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full p-4 pl-12 pr-10 bg-[#0e0e10] border border-gray-700 rounded-2xl text-white placeholder-gray-200 focus:outline-none focus:border-[#00FFA3] focus:ring-2 focus:ring-[#00FFA3]/20 shadow-xl text-sm"
            />
            <svg
              className="w-5 h-5 text-gray-100 absolute left-4 top-1/2 -translate-y-1/2 pt-1"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            {searchTerm && (
              <button
                onClick={() => setSearchTerm('')}
                className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-100 hover:text-white text-sm font-bold pt-1"
              >
                ✕
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl flex items-center justify-between hover:border-[#00FFA3]/30 hover:shadow-[0_0_15px_rgba(0,255,163,0.1)] transition-all">
        <div className="flex items-center gap-4">
          <span className="text-2xl">📡</span>
          <div>
            <p className="text-xs text-gray-100 font-bold uppercase tracking-wider">실시간 방송 현황</p>
            <h3 className="text-lg font-black text-white">
              {totalLiveCount}개 방송 중{' '}
              <span className="text-xs text-gray-100 font-normal">
                / Top {defaultStreamers.length || 100}
              </span>
            </h3>
            <p className="text-xs text-gray-100 font-medium">매일 새벽 04:00 자동 정산</p>
          </div>
        </div>
        <div className="text-right font-mono">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-[#00FFA3]/15 text-[#00FFA3] border border-[#00FFA3]/30">
            <span className="w-2 h-2 rounded-full bg-[#00FFA3] animate-pulse" />
            정상 가동
          </span>
        </div>
      </div>

      <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
              <span>📊</span> 30일 평균 시청자 순위
            </h3>
            <div className="relative group/tip flex items-center">
              <button
                type="button"
                className="w-4 h-4 rounded-full border border-gray-600 flex items-center justify-center text-[10px] font-bold text-gray-200 hover:text-[#00FFA3] hover:border-[#00FFA3] transition-colors focus:outline-none"
                aria-label="30일 평균 시청자 순위 산출 방식 안내"
              >
                i
              </button>
              <div className="absolute left-0 bottom-full mb-2 hidden group-hover/tip:block w-72 sm:w-80 p-3.5 bg-[#18181c] border border-gray-700 text-xs text-gray-200 rounded-xl shadow-2xl z-50 font-normal leading-relaxed text-left backdrop-blur-md pointer-events-none break-keep">
                <p className="font-semibold text-white mb-1.5 flex items-center gap-1.5">
                  <span>📊</span> 30일 평균 시청자 순위 산출 기준
                </p>
                <div className="space-y-1.5 text-gray-100">
                  <p>
                    최근 30일간 방송의 길이를 반영한 <span className="text-white font-medium">시간 가중 평균 시청자 수</span>를 기준으로 순위를 산정합니다.
                  </p>
                  <div className="text-[11px] text-gray-200 border-t border-gray-800 pt-1.5 space-y-1">
                    <p>• <span className="text-white font-medium">시간 가중치:</span> 총 시청 시간(View Hours) ÷ 총 방송 시간</p>
                    <p>• <span className="text-white font-medium">노이즈 배제:</span> 5분 미만의 테스트/튕김 방송 집계 제외</p>
                    <p>• <span className="text-white font-medium">정산 주기:</span> 매일 새벽 04:00 일괄 갱신 스냅샷</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <span className="text-xs text-gray-100 font-mono font-semibold">
            {isSearching ? `검색 결과 ${streams.length}명` : `${streams.length}명 랭크`}
          </span>
        </div>

        {isLoading && streams.length === 0 ? (
          <div className="py-16 text-center text-gray-200 font-medium">
            <div className="w-8 h-8 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            스트리머 랭킹 데이터를 집계하는 중입니다...
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-100 min-w-[760px]">
              <thead>
                <tr className="border-b border-gray-800 text-[11px] font-bold text-gray-200 uppercase tracking-wider">
                  <th className="py-3 px-3 w-12 text-center whitespace-nowrap">순위</th>
                  <th className="py-3 px-4 min-w-[200px]">스트리머</th>
                  <th className="py-3 px-4 whitespace-nowrap min-w-[120px]">주력 카테고리</th>
                  <th className="py-3 px-4 text-right whitespace-nowrap min-w-[130px]">30일 평균 시청자</th>
                  <th className="py-3 px-4 text-right whitespace-nowrap min-w-[110px]">실시간 시청자</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap min-w-[90px]">방송 상태</th>
                  <th className="py-3 px-4 text-center whitespace-nowrap w-24">전적 분석</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/60 font-mono">
                {streams.map((stream, idx) => {
                  const isStreaming = stream.status === 'LIVE' || stream.status === 'ANALYZING';
                  return (
                    <tr
                      key={stream.streamId}
                      onClick={() => handleRowClick(stream.streamId)}
                      className="transition-all duration-200 cursor-pointer group hover:bg-[#16231c]/70 hover:shadow-[inset_0_0_0_1px_rgba(0,255,163,0.5),0_0_16px_rgba(0,255,163,0.15)]"
                    >
                      <td className="py-3.5 px-3 text-center whitespace-nowrap">
                        <span
                          className={`inline-block w-6 h-6 rounded-md leading-6 text-xs font-black ${
                            idx === 0
                              ? 'bg-[#00FFA3] text-black shadow-sm'
                              : idx === 1
                              ? 'bg-slate-300 text-black'
                              : idx === 2
                              ? 'bg-amber-600 text-white'
                              : 'text-gray-100 font-bold'
                          }`}
                        >
                          {idx + 1}
                        </span>
                      </td>

                      <td className="py-3.5 px-4 font-sans">
                        <div className="flex items-center gap-3">
                          <div className="relative shrink-0">
                            <img
                              src={stream.profileImageUrl || '/cheese-pick-logo.png'}
                              alt={stream.streamerName}
                              className="w-10 h-10 rounded-full object-cover border border-gray-800"
                            />
                            {isStreaming && (
                              <span className="absolute bottom-0 right-0 w-3 h-3 bg-red-500 border-2 border-[#141416] rounded-full" />
                            )}
                          </div>
                          <div className="min-w-0">
                            <p className="font-extrabold text-white text-[15px] group-hover:text-[#00FFA3] transition-colors truncate">
                              {stream.streamerName}
                            </p>
                            <p className="text-xs text-gray-100 group-hover:text-white transition-colors truncate max-w-xs sm:max-w-md">
                              {stream.liveTitle || '최근 방송 기록 없음'}
                            </p>
                          </div>
                        </div>
                      </td>

                      <td className="py-3.5 px-4 font-sans whitespace-nowrap">
                        <span
                          className="inline-block max-w-[130px] sm:max-w-[180px] truncate whitespace-nowrap align-middle px-2.5 py-1 rounded-md text-xs font-bold bg-[#13221a] text-[#00FFA3]/90 border border-[#00FFA3]/30 tracking-wide shadow-xs"
                          title={stream.categoryName || '기타'}
                        >
                          {stream.categoryName || '기타'}
                        </span>
                      </td>

                      <td className="py-3.5 px-4 text-right font-bold text-gray-100 whitespace-nowrap">
                        {(stream.averageViewers ?? 0) > 0
                          ? `${(stream.averageViewers ?? 0).toLocaleString()}명`
                          : '-'}
                      </td>

                      <td className="py-3.5 px-4 text-right font-bold text-[#00FFA3] whitespace-nowrap">
                        {isStreaming && (stream.concurrentUserCount ?? 0) > 0
                          ? `${(stream.concurrentUserCount ?? 0).toLocaleString()}명`
                          : '-'}
                      </td>

                      <td className="py-3.5 px-4 text-center whitespace-nowrap">
                        {isStreaming && (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-red-500/15 text-red-400 border border-red-500/30 shadow-[0_0_10px_rgba(239,68,68,0.25)]">
                            <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                            LIVE
                          </span>
                        )}
                        {!isStreaming && (
                          <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-gray-800/70 text-gray-100 border border-gray-700">
                            OFFLINE
                          </span>
                        )}
                      </td>

                      <td className="py-3.5 px-4 text-center whitespace-nowrap">
                        <span className="inline-block px-2.5 py-1 text-xs font-bold rounded-lg bg-[#1e1e24] group-hover:bg-[#00FFA3] group-hover:text-black transition-all text-gray-100">
                          전적 →
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
