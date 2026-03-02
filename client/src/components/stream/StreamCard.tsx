import { useNavigate } from 'react-router-dom';
import type { StreamItem } from "../../types/stream.ts";

export const StreamCard = ({ stream }: { stream: StreamItem }) => {
  const navigate = useNavigate();

  return (
      // 💡 세로 정렬(flex-col), 가운데 정렬(items-center) 적용
      <div
          onClick={() => navigate(`/streams/${stream.streamId}`)}
          className="flex flex-col items-center p-6 bg-gray-800/80 backdrop-blur-sm border border-white/5 rounded-2xl cursor-pointer hover:bg-gray-700/80 hover:-translate-y-1 hover:shadow-xl hover:border-chzzk-green/30 transition-all duration-200 group"
      >
        {/* 프로필 이미지 영역 */}
        <div className="w-24 h-24 mb-4 rounded-full overflow-hidden bg-gray-900 ring-4 ring-gray-700 group-hover:ring-chzzk-green transition-all duration-200 flex-shrink-0">
          <img
              src={stream.profileImageUrl}
              alt={stream.streamerName}
              className="w-full h-full object-cover"
          />
        </div>

        {/* 텍스트 정보 영역 (가운데 정렬) */}
        <div className="flex flex-col w-full text-center">
          <h3 className="text-white font-bold text-lg truncate mb-1">
            {stream.streamerName}
          </h3>
          <p className="text-gray-400 text-sm truncate mb-3">
            {stream.liveTitle}
          </p>

          {/* 카테고리 뱃지 */}
          <div className="flex justify-center">
          <span className="px-3 py-1 bg-white/5 text-gray-300 text-xs rounded-full border border-white/10 truncate max-w-full">
            {stream.categoryName || '카테고리 없음'}
          </span>
          </div>
        </div>
      </div>
  );
};
