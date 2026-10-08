// src/components/stream/StreamCard.tsx

import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { StreamItem } from "../../types/stream";

export const StreamCard: React.FC<{ stream: StreamItem }> = ({ stream }) => {
  const navigate = useNavigate();
  // 오프라인 상태 판별
  const isOffline = stream.status === 'OFFLINE';

  return (
      <div
          onClick={() => navigate(`/streams/${stream.streamId}`)}
          /* 오프라인일 경우 투명도와 흑백(grayscale) 필터를 적용하여 시각적으로 구분 */
          className={`flex items-center p-3 sm:p-4 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-500 rounded-xl cursor-pointer hover:border-[#00FFA3] hover:ring-1 hover:ring-[#00FFA3] hover:shadow-lg transition-all duration-200 group ${isOffline ? 'opacity-50 grayscale' : ''}`}
      >
        <div
          onClick={(e) => {
            e.stopPropagation();
            navigate(`/streamers/${stream.streamId}`);
          }}
          title={`${stream.streamerName} 스트리머 리포트 보기`}
          className={`relative w-12 h-12 sm:w-14 sm:h-14 rounded-full overflow-hidden shrink-0 border-2 ${isOffline ? 'border-gray-600' : 'border-gray-600 group-hover:border-[#00FFA3]'} hover:ring-2 hover:ring-[#00FFA3] transition-all cursor-pointer`}
        >
          <img
            src={stream.profileImageUrl ?? undefined}
            alt={stream.streamerName}
            className="w-full h-full object-cover"
            loading="lazy"
            decoding="async"
          />
        </div>

        <div className="ml-3 sm:ml-4 flex flex-col flex-1 min-w-0">
          <h3 className="text-white font-semibold text-sm sm:text-base truncate mb-0.5 group-hover:text-[#00FFA3] transition-colors">
            {stream.liveTitle}
          </h3>

          <div className="flex items-center gap-2 mt-1">
            <span
              onClick={(e) => {
                e.stopPropagation();
                navigate(`/streamers/${stream.streamId}`);
              }}
              title={`${stream.streamerName} 스트리머 리포트 보기`}
              className="text-gray-100 hover:text-[#00FFA3] hover:underline text-xs sm:text-sm font-medium truncate max-w-[90px] sm:max-w-none transition-colors cursor-pointer"
            >
              {stream.streamerName}
            </span>

            {/* 상태에 따른 뱃지 분기 처리 */}
            {isOffline ? (
                <span className="px-2 py-0.5 bg-gray-600 text-white text-[10px] sm:text-xs rounded-full whitespace-nowrap font-bold ml-auto">
                  오프라인
                </span>
            ) : (
                <>
                  <span className="px-2 py-0.5 bg-[#1e1e24] border border-gray-700 text-gray-100 text-[10px] sm:text-xs rounded-full whitespace-nowrap truncate max-w-[100px] sm:max-w-none font-medium">
                    {stream.categoryName || '카테고리 없음'}
                  </span>

                  {/* 시청자 수 및 치지직 라이브 바로가기 링크 */}
                  <div className="flex items-center gap-2 ml-auto shrink-0">
                    {(stream.concurrentUserCount ?? 0) > 0 && (
                      <div className="flex items-center gap-1.5">
                        <div className="w-1.5 h-1.5 rounded-full bg-red-500"></div>
                        <span className="text-red-500 text-[11px] sm:text-sm font-black tracking-tight">
                          {stream.concurrentUserCount?.toLocaleString()}
                        </span>
                      </div>
                    )}

                    <a
                      href={`https://chzzk.naver.com/live/${stream.streamId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      title="치지직 라이브 바로가기 (새 탭)"
                      className="p-1 sm:p-1.5 rounded-lg bg-[#141416] hover:bg-[#00FFA3]/15 border border-gray-700 hover:border-[#00FFA3]/60 text-gray-400 hover:text-[#00FFA3] transition-all flex items-center justify-center group/btn"
                    >
                      <svg
                        className="w-3.5 h-3.5 transform group-hover/btn:translate-x-0.5 group-hover/btn:-translate-y-0.5 transition-transform"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                        />
                      </svg>
                    </a>
                  </div>
                </>
            )}
          </div>
        </div>
      </div>
  );
};
