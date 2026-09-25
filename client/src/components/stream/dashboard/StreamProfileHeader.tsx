import React from 'react';
import { useNavigate } from 'react-router-dom';

interface Props {
  streamId: string;
  streamerName?: string;
  profileImageUrl?: string | null;
  isLive: boolean;
  status?: string;
  viewers?: number;
  liveTitle?: string | null;
  categoryName?: string | null;
  isLiveTab?: boolean;
}

export const StreamProfileHeader: React.FC<Props> = ({
  streamId,
  streamerName,
  profileImageUrl,
  isLive,
  viewers,
  liveTitle,
  categoryName,
  isLiveTab
}) => {
  const navigate = useNavigate();

  return (
    <div className="mb-6 sm:mb-8 p-4 sm:p-6 bg-[#1a1a1c] border border-gray-800 rounded-2xl flex items-center justify-between gap-4 sm:gap-6">
      <div className="flex items-center gap-4 sm:gap-6 min-w-0 flex-1">
        <div
          onClick={() => navigate(`/streamers/${streamId}`)}
          title={`${streamerName || '스트리머'} 리포트로 이동`}
          className={`shrink-0 w-14 h-14 sm:w-20 sm:h-20 rounded-full border-2 ${isLive ? 'border-[#00FFA3]/30 shadow-[0_0_12px_rgba(0,255,163,0.15)]' : 'border-gray-600 grayscale'} overflow-hidden bg-gray-900 shadow-xl cursor-pointer hover:ring-2 hover:ring-[#00FFA3] transition-all`}
        >
          <img
            src={profileImageUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${streamId}`}
            alt="profile"
            className="w-full h-full object-cover"
          />
        </div>

        <div className="flex-1 min-w-0 overflow-hidden">
          <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 mb-1">
            <button
              onClick={() => navigate(`/streamers/${streamId}`)}
              title={`${streamerName || '스트리머'} 리포트로 이동`}
              className="text-white hover:text-[#00FFA3] font-bold text-base sm:text-lg truncate max-w-[140px] sm:max-w-none text-left transition-colors cursor-pointer"
            >
              {streamerName || '정보 로딩 중...'}
            </button>

            {isLiveTab ? (
              <span className="px-2 sm:px-2.5 py-0.5 text-[11px] sm:text-xs font-black bg-red-600 text-white rounded-md flex items-center gap-1.5 shadow-sm">
                <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse"></span>
                LIVE 실시간 관제 중
              </span>
            ) : (
              <span className="px-2 sm:px-2.5 py-0.5 text-[11px] sm:text-xs font-semibold bg-[#26262b] text-gray-100 border border-gray-700/60 rounded-md flex items-center gap-1.5">
                📁 방송 아카이브
              </span>
            )}

            {isLiveTab && viewers !== undefined && (
              <span className="text-[#00FFA3] text-xs sm:text-sm font-bold sm:ml-2">
                👤 {viewers.toLocaleString()}명 시청 중
              </span>
            )}

            {categoryName && (
              <span className="px-2 py-0.5 bg-purple-900/40 border border-purple-500/50 text-purple-300 text-[10px] sm:text-[11px] font-bold rounded-sm whitespace-nowrap">
                {categoryName}
              </span>
            )}
          </div>

          <h1 className="text-lg sm:text-2xl font-black text-white truncate block w-full mt-1" title={liveTitle || '방송 제목 정보 없음'}>
            {liveTitle || '방송 제목 정보 없음'}
          </h1>
        </div>
      </div>

      <button
        onClick={() => navigate(`/streamers/${streamId}`)}
        className="hidden sm:inline-flex items-center gap-1.5 px-3.5 py-2.5 bg-[#26262b] hover:bg-[#00FFA3]/15 text-gray-200 hover:text-[#00FFA3] border border-gray-700 hover:border-[#00FFA3]/40 rounded-xl text-xs font-bold transition-all shrink-0 group"
        title={`${streamerName || '스트리머'} 리포트로 이동`}
      >
        <span>📊 스트리머 리포트</span>
        <svg className="w-3.5 h-3.5 transform group-hover:translate-x-0.5 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
      </button>
    </div>
  );
};
