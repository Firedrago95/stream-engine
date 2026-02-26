import React from 'react';
import type {StreamItem} from '../../types/stream';

export const StreamCard: React.FC<{ stream: StreamItem }> = ({ stream }) => {
  const isAnalyzing = stream.status === 'ANALYZING';

  return (
      <div className="group flex flex-col gap-2.5 cursor-pointer">
        {/* Thumbnail */}
        <div className="relative w-full aspect-video rounded-xl overflow-hidden bg-chzzk-card ring-1 ring-white/5 group-hover:ring-chzzk-green/50 transition-all duration-300">
          <img
              src={stream.thumbnailUrl}
              className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
              alt={stream.liveTitle}
          />
          {/* Floating Badges */}
          <div className="absolute top-2 left-2 flex gap-1.5">
          <span className={`px-1.5 py-0.5 rounded text-[11px] font-bold ${
              isAnalyzing ? 'bg-purple-600 shadow-analyzing animate-pulse' : 'bg-chzzk-green text-black'
          }`}>
            {isAnalyzing ? 'ANALYZING' : 'LIVE'}
          </span>
          </div>
          <div className="absolute bottom-2 left-2">
          <span className="px-1.5 py-0.5 rounded text-[11px] font-medium bg-black/60 backdrop-blur-md text-gray-200">
            {stream.categoryName}
          </span>
          </div>
        </div>
        {/* Info */}
        <div className="px-0.5">
          <h3 className="text-[15px] font-semibold text-gray-100 line-clamp-2 leading-snug group-hover:text-chzzk-green transition-colors">
            {stream.liveTitle}
          </h3>
          <p className="text-sm text-gray-400 mt-1 flex items-center gap-1.5">
            {stream.streamerName}
          </p>
        </div>
      </div>
  );
};
