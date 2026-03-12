import React from 'react';

interface Props {
  streamId: string;
  streamerName?: string;
  profileImageUrl?: string;
  isLive: boolean;
  onToggleLive: () => void;
}

export const StreamProfileHeader: React.FC<Props> = ({ streamId, streamerName, profileImageUrl, isLive, onToggleLive }) => (
    <div className="mb-8 p-6 bg-[#1a1a1c] border border-gray-800 rounded-2xl flex items-center gap-6">
      <div className="w-20 h-20 rounded-full border-2 border-[#00FFA3]/20 overflow-hidden bg-gray-900 shadow-xl">
        <img
            src={profileImageUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${streamId}`}
            alt="profile"
            className="w-full h-full object-cover"
        />
      </div>
      <div className="flex-1">
        <div className="flex items-center gap-2 mb-1">
          <span className="text-white font-bold text-lg">{streamerName || '정보 로딩 중...'}</span>
          <span className={`px-2 py-0.5 text-[10px] ${isLive ? 'bg-red-600 animate-pulse' : 'bg-gray-800'} text-white rounded-sm font-black`}>{isLive ? 'LIVE' : 'OFFLINE'}</span>
          <button onClick={onToggleLive} className="ml-2 text-[10px] text-gray-600 border border-gray-800 px-1 py-0.5 rounded hover:text-white">상태 전환</button>
        </div>
        <h1 className="text-2xl font-black text-white">치즈슬라이스 실시간 대시보드</h1>
      </div>
    </div>
);
