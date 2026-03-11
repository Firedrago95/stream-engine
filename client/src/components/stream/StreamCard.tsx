import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { StreamItem } from "../../types/stream";

export const StreamCard: React.FC<{ stream: StreamItem }> = ({ stream }) => {
  const navigate = useNavigate();

  return (
      <div
          onClick={() => navigate(`/streams/${stream.streamId}`)}
          /* 💡 bg-gray-700은 우리가 style.css에서 정의한 짙은 회색(#1a1a1c)이 적용됩니다. */
          className="flex items-center p-3 sm:p-4 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-500 rounded-xl cursor-pointer hover:border-[#00FFA3]/50 hover:shadow-lg transition-all duration-200 group"
      >
        {/* 좌측: 프로필 이미지 */}
        <div className="relative w-12 h-12 sm:w-14 sm:h-14 rounded-full overflow-hidden shrink-0 border-2 border-gray-600 group-hover:border-[#00FFA3] transition-colors">
          <img
              src={stream.profileImageUrl ?? undefined}
              alt={stream.streamerName}
              className="w-full h-full object-cover"
          />
          <div className="absolute bottom-0 right-0 w-3 h-3 bg-red-500 border-2 border-gray-900 rounded-full animate-pulse"></div>
        </div>

        {/* 우측: 방송 정보 */}
        <div className="ml-4 flex flex-col flex-1 min-w-0">
          {/* 💡 text-gray-100은 우리가 정의한 눈이 편한 흰색(#e2e2e5)입니다. */}
          <h3 className="text-gray-800 dark:text-gray-100 font-semibold text-sm sm:text-base truncate mb-0.5 group-hover:text-[#00FFA3] transition-colors">
            {stream.liveTitle}
          </h3>

          <div className="flex items-center gap-2 mt-1">
          <span className="text-gray-500 dark:text-gray-200 text-xs sm:text-sm truncate">
            {stream.streamerName}
          </span>
            {/* 카테고리 뱃지 */}
            <span className="px-2 py-0.5 bg-gray-100 dark:bg-gray-400/50 border border-gray-200 dark:border-gray-300 text-gray-600 dark:text-gray-100 text-[10px] sm:text-xs rounded-full whitespace-nowrap">
            {stream.categoryName || '카테고리 없음'}
          </span>
          </div>
        </div>
      </div>
  );
};
