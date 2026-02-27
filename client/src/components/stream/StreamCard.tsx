import type {StreamItem} from "../../types/stream.ts";

export const StreamCard = ({ stream }: { stream: StreamItem }) => {
  return (
      <div className="flex items-center gap-4 p-4 bg-gray-800 rounded-xl cursor-pointer hover:bg-gray-700 transition-colors">

        {/* 프로필 이미지 영역: 크기를 고정하고 완벽한 원형으로 만듭니다 */}
        <div className="flex-shrink-0 w-16 h-16 rounded-full overflow-hidden bg-gray-600 border-2 border-purple-500">
          <img
              src={stream.profileImageUrl}
              alt={stream.streamerName}
              className="w-full h-full object-cover"
          />
        </div>

        {/* 텍스트 정보 영역 */}
        <div className="flex flex-col overflow-hidden">
          <h3 className="text-white font-bold text-lg truncate">
            {stream.streamerName}
          </h3>
          <p className="text-gray-300 text-sm truncate">
            {stream.liveTitle}
          </p>
          <p className="text-gray-500 text-xs mt-1">
            {stream.categoryName || '카테고리 없음'}
          </p>
        </div>

      </div>
  );
};
