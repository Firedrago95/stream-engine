import React, { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStreamers } from '../../hooks/useStreamers';

const POPULAR_TAGS = ['침착맨', '풍월량', '랄로', '녹두로', '우왁굳', '옥냥이', '따효니', '한동숙'];

export const StreamerStatsPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const { streamers: streams, isLoading } = useStreamers(searchTerm, 30000);

  const topViewers = useMemo(() => {
    if (!streams || streams.length === 0) return null;
    return [...streams].sort((a, b) => (b.concurrentUserCount ?? 0) - (a.concurrentUserCount ?? 0))[0];
  }, [streams]);

  const activeLiveCount = useMemo(() => {
    if (!streams) return 0;
    return streams.filter((s) => s.status === 'LIVE' || s.status === 'ANALYZING').length;
  }, [streams]);

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

          <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
            <span className="text-xs text-gray-200 font-bold">추천 검색:</span>
            {POPULAR_TAGS.map((tag) => (
              <button
                key={tag}
                onClick={() => setSearchTerm(tag)}
                className="px-2.5 py-1 text-xs font-semibold rounded-lg bg-[#1a1a1c] border border-gray-800 text-gray-100 hover:border-[#00FFA3] hover:text-[#00FFA3] hover:shadow-[0_0_10px_rgba(0,255,163,0.2)] transition-all"
              >
                {tag}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {topViewers && (
          <div
            onClick={() => handleRowClick(topViewers.streamId)}
            className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl hover:border-[#00FFA3]/50 hover:shadow-[0_0_20px_rgba(0,255,163,0.15)] transition-all cursor-pointer group flex items-center justify-between"
          >
            <div className="flex items-center gap-4">
              <span className="text-2xl">👑</span>
              <div>
                <p className="text-xs text-[#00FFA3] font-bold uppercase tracking-wider">30일 체급 1위</p>
                <h3 className="text-lg font-black text-white group-hover:text-[#00FFA3] transition-colors">
                  {topViewers.streamerName}
                </h3>
                <p className="text-xs text-gray-100 truncate max-w-xs">{topViewers.categoryName || '기타'}</p>
              </div>
            </div>
            <div className="text-right font-mono">
              <p className="text-xl font-black text-white">
                {(topViewers.averageViewers ?? topViewers.concurrentUserCount ?? 0).toLocaleString()}명
              </p>
              <span className="text-[11px] text-[#00FFA3] font-semibold">30일 평균 시청자</span>
            </div>
          </div>
        )}

        <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl flex items-center justify-between hover:border-[#00FFA3]/30 hover:shadow-[0_0_15px_rgba(0,255,163,0.1)] transition-all">
          <div className="flex items-center gap-4">
            <span className="text-2xl">📡</span>
            <div>
              <p className="text-xs text-gray-100 font-bold uppercase tracking-wider">실시간 방송 현황</p>
              <h3 className="text-lg font-black text-white">
                {activeLiveCount}개 방송 중 <span className="text-xs text-gray-100 font-normal">/ Top {streams.length}</span>
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
      </div>

      <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
              <span>🏆</span> 스트리머 체급 랭킹 리더보드
            </h3>
          </div>
          <span className="text-xs text-gray-100 font-mono font-semibold">
            {streams.length}명 랭크
          </span>
        </div>

        {isLoading && streams.length === 0 ? (
          <div className="py-16 text-center text-gray-200 font-medium">
            <div className="w-8 h-8 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
            스트리머 랭킹 데이터를 집계하는 중입니다...
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-100">
              <thead>
                <tr className="border-b border-gray-800 text-[11px] font-bold text-gray-200 uppercase tracking-wider">
                  <th className="py-3 px-3 w-12 text-center">순위</th>
                  <th className="py-3 px-4">스트리머</th>
                  <th className="py-3 px-4">주력 카테고리</th>
                  <th className="py-3 px-4 text-right">
                    <div className="inline-flex items-center gap-1 cursor-help group/tip relative">
                      <span>30일 평균 시청자</span>
                      <svg className="w-3.5 h-3.5 text-gray-100 group-hover/tip:text-[#00FFA3] transition-colors fill-current" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                      </svg>
                      <div className="absolute right-0 bottom-full mb-2 hidden group-hover/tip:block w-64 p-3 bg-[#18181c] border border-gray-700 text-[11px] text-gray-200 rounded-xl shadow-2xl z-50 font-normal leading-relaxed text-left backdrop-blur-md">
                        💡 최근 30일간의 방송 세션 데이터를 바탕으로 산출된 순수 체급 지표입니다. (매일 새벽 04:00 정기 갱신)
                      </div>
                    </div>
                  </th>
                  <th className="py-3 px-4 text-right">실시간 시청자</th>
                  <th className="py-3 px-4 text-center">방송 상태</th>
                  <th className="py-3 px-4 text-center w-24">전적 분석</th>
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
                      <td className="py-3.5 px-3 text-center">
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

                      <td className="py-3.5 px-4 font-sans">
                        <span className="px-2.5 py-1 rounded-md text-xs font-bold bg-[#13221a] text-[#00FFA3]/90 border border-[#00FFA3]/30 tracking-wide shadow-xs">
                          {stream.categoryName || '기타'}
                        </span>
                      </td>

                      <td className="py-3.5 px-4 text-right font-bold text-gray-100">
                        {(stream.averageViewers ?? 0) > 0
                          ? `${(stream.averageViewers ?? 0).toLocaleString()}명`
                          : '-'}
                      </td>

                      <td className="py-3.5 px-4 text-right font-bold text-[#00FFA3]">
                        {isStreaming && (stream.concurrentUserCount ?? 0) > 0
                          ? `${(stream.concurrentUserCount ?? 0).toLocaleString()}명`
                          : '-'}
                      </td>

                      <td className="py-3.5 px-4 text-center">
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

                      <td className="py-3.5 px-4 text-center">
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
