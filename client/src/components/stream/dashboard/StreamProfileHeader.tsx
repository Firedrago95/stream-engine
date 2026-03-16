import React from 'react';

interface Props {
  streamId: string;
  streamerName?: string;
  profileImageUrl?: string;
  isLive: boolean;
  status?: string;
  viewers?: number;
  liveTitle?: string;
  categoryName?: string;
}

export const StreamProfileHeader: React.FC<Props> = ({ streamId, streamerName, profileImageUrl, isLive, status, viewers, liveTitle, categoryName }) => (
    <div className="mb-8 p-6 bg-[#1a1a1c] border border-gray-800 rounded-2xl flex items-center gap-6">

      {/* 1. shrink-0 추가: 방제가 아무리 길어도 프로필 사진이 찌그러지지 않게 방어 */}
      <div className={`shrink-0 w-20 h-20 rounded-full border-2 ${isLive ? 'border-[#00FFA3]/20' : 'border-gray-600 grayscale'} overflow-hidden bg-gray-900 shadow-xl`}>
        <img
            src={profileImageUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${streamId}`}
            alt="profile"
            className="w-full h-full object-cover"
        />
      </div>

      <div className="flex-1 min-w-0 w-full overflow-hidden">

        {/* 3. flex-wrap 추가: 화면이 좁을 때 뱃지들이 방제 위에서 예쁘게 줄바꿈되도록 처리 */}
        <div className="flex flex-wrap items-center gap-2 mb-1">
          <span className="text-white font-bold text-lg">{streamerName || '정보 로딩 중...'}</span>

          <span className={`px-2 py-0.5 text-[10px] ${isLive ? 'bg-red-600 animate-pulse' : 'bg-gray-600'} text-white rounded-sm font-black`}>
            {status === 'ANALYZING' ? '분석 중' : isLive ? 'LIVE' : 'OFFLINE'}
          </span>

          {isLive && viewers !== undefined && (
              <span className="text-[#00FFA3] text-sm font-bold ml-2">
              👤 {viewers.toLocaleString()}명 시청 중
            </span>
          )}

          {categoryName && (
              <span className="px-2 py-0.5 bg-purple-900/40 border border-purple-500/50 text-purple-300 text-[11px] font-bold rounded-sm whitespace-nowrap">
              {categoryName}
            </span>
          )}
        </div>

        <h1 className="text-xl sm:text-2xl font-black text-white truncate block w-full mt-1" title={liveTitle || '방송 제목 정보 없음'}>
          {liveTitle || '방송 제목 정보 없음'}
        </h1>
      </div>

    </div>
);
