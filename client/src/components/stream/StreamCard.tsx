import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { StreamItem } from "../../types/stream";

export const StreamCard: React.FC<{ stream: StreamItem }> = ({ stream }) => {
  const navigate = useNavigate();

  return (
      <div
          onClick={() => navigate(`/streams/${stream.streamId}`)}
          className="flex items-center p-3 sm:p-4 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl cursor-pointer hover:border-[#00FFA3]/50 hover:shadow-lg transition-all duration-200 group"
      >
        {/* 💡 좌측: 작은 프로필 이미지 (소프트콘 스타일) */}
        <div className="relative w-12 h-12 sm:w-14 sm:h-14 rounded-full overflow-hidden shrink-0 border-2 border-slate-700 group-hover:border-[#00FFA3] transition-colors">
          <img
              src={stream.profileImageUrl}
              alt={stream.streamerName}
              className="w-full h-full object-cover"
          />
          {/* LIVE 빨간 불빛 효과 */}
          <div className="absolute bottom-0 right-0 w-3 h-3 bg-red-500 border-2 border-slate-800 rounded-full animate-pulse"></div>
        </div>

        {/* 💡 우측: 밀도 높은 정보 배치 */}
        <div className="ml-4 flex flex-col flex-1 min-w-0">
          {/* 방 제목 */}
          <h3 className="text-slate-800 dark:text-slate-100 font-semibold text-sm sm:text-base truncate mb-0.5 group-hover:text-[#00FFA3] transition-colors">
            {stream.liveTitle}
          </h3>

          <div className="flex items-center gap-2 mt-1">
            {/* 스트리머 이름 */}
            <span className="text-slate-500 dark:text-slate-400 text-xs sm:text-sm truncate">
            {stream.streamerName}
          </span>
            {/* 카테고리 뱃지 (Mosaic Badge UI 응용) */}
            <span className="px-2 py-0.5 bg-slate-100 dark:bg-slate-700/50 border border-slate-200 dark:border-slate-600 text-slate-600 dark:text-slate-300 text-[10px] sm:text-xs rounded-full whitespace-nowrap">
            {stream.categoryName || '카테고리 없음'}
          </span>
          </div>
        </div>
      </div>
  );
};
