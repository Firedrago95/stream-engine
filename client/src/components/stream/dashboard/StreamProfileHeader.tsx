import React from 'react';

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
}) => (
  <div className="mb-6 sm:mb-8 p-4 sm:p-6 bg-[#1a1a1c] border border-gray-800 rounded-2xl flex items-center gap-4 sm:gap-6">
    <div className={`shrink-0 w-14 h-14 sm:w-20 sm:h-20 rounded-full border-2 ${isLive ? 'border-[#00FFA3]/30 shadow-[0_0_12px_rgba(0,255,163,0.15)]' : 'border-gray-600 grayscale'} overflow-hidden bg-gray-900 shadow-xl`}>
      <img
        src={profileImageUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${streamId}`}
        alt="profile"
        className="w-full h-full object-cover"
      />
    </div>

    <div className="flex-1 min-w-0 w-full overflow-hidden">
      <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 mb-1">
        <span className="text-white font-bold text-base sm:text-lg truncate max-w-[140px] sm:max-w-none">{streamerName || '정보 로딩 중...'}</span>

        {isLiveTab ? (
          <span className="px-2 sm:px-2.5 py-0.5 text-[11px] sm:text-xs font-black bg-red-600 text-white rounded-md flex items-center gap-1.5 shadow-sm">
            <span className="w-1.5 h-1.5 rounded-full bg-white animate-pulse"></span>
            LIVE 실시간 관제 중
          </span>
        ) : (
          <span className="px-2 sm:px-2.5 py-0.5 text-[11px] sm:text-xs font-semibold bg-[#26262b] text-gray-300 border border-gray-700/60 rounded-md flex items-center gap-1.5">
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
);
