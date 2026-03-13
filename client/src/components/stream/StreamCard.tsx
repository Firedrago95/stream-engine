import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { StreamItem } from "../../types/stream";

export const StreamCard: React.FC<{ stream: StreamItem }> = ({ stream }) => {
  const navigate = useNavigate();
  // 💡 오프라인 상태 판별
  const isOffline = stream.status === 'OFFLINE';

  return (
      <div
          onClick={() => navigate(`/streams/${stream.streamId}`)}
          /* 💡 오프라인일 경우 투명도와 흑백(grayscale) 필터를 적용하여 시각적으로 구분 */
          className={`flex items-center p-3 sm:p-4 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-500 rounded-xl cursor-pointer hover:border-[#00FFA3]/50 hover:shadow-lg transition-all duration-200 group ${isOffline ? 'opacity-50 grayscale' : ''}`}
      >
        <div className={`relative w-12 h-12 sm:w-14 sm:h-14 rounded-full overflow-hidden shrink-0 border-2 ${isOffline ? 'border-gray-600' : 'border-gray-600 group-hover:border-[#00FFA3]'} transition-colors`}>
          <img
              src={stream.profileImageUrl ?? undefined}
              alt={stream.streamerName}
              className="w-full h-full object-cover"
          />
          {/* 라이브 중일 때만 빨간색 점(Pulse) 표시 */}
          {!isOffline && (
              <div className="absolute bottom-0 right-0 w-3 h-3 bg-red-500 border-2 border-gray-900 rounded-full animate-pulse"></div>
          )}
        </div>

        <div className="ml-4 flex flex-col flex-1 min-w-0">
          <h3 className="text-gray-800 dark:text-gray-100 font-semibold text-sm sm:text-base truncate mb-0.5 group-hover:text-[#00FFA3] transition-colors">
            {stream.liveTitle}
          </h3>

          <div className="flex items-center gap-2 mt-1">
            <span className="text-gray-500 dark:text-gray-200 text-xs sm:text-sm truncate">
              {stream.streamerName}
            </span>

            {/* 상태에 따른 뱃지 분기 처리 */}
            {isOffline ? (
                <span className="px-2 py-0.5 bg-gray-600 text-white text-[10px] sm:text-xs rounded-full whitespace-nowrap font-bold">
                  오프라인
                </span>
            ) : (
                <>
                  <span className="px-2 py-0.5 bg-gray-100 dark:bg-gray-400/50 border border-gray-200 dark:border-gray-300 text-gray-600 dark:text-gray-100 text-[10px] sm:text-xs rounded-full whitespace-nowrap">
                    {stream.categoryName || '카테고리 없음'}
                  </span>
                  {/* 💡 시청자 수 노출 */}
                  <span className="text-[#00FFA3] text-[10px] sm:text-xs font-bold flex items-center gap-1">
                    👤 {stream.concurrentUserCount?.toLocaleString()}명
                  </span>
                </>
            )}
          </div>
        </div>
      </div>
  );
};
