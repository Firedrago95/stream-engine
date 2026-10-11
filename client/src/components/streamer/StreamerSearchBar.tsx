import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useStreamers } from '../../hooks/useStreamers';

export const StreamerSearchBar: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const trimmed = searchTerm.trim();
  const isSearching = trimmed.length > 0;

  // 타이핑 시에만 검색 API 호출
  const { streamers: searchResults, isLoading } = useStreamers(trimmed, 30000, isSearching);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // ESC 키로 닫기
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setIsOpen(false);
    }
  };

  const handleSelectStreamer = (streamId: string) => {
    setIsOpen(false);
    setSearchTerm('');
    navigate(`/streamers/${streamId}`);
  };

  return (
    <div ref={containerRef} className="relative w-full max-w-xl mx-auto pt-2" onKeyDown={handleKeyDown}>
      <div className="relative">
        <input
          type="text"
          placeholder="스트리머 검색 (오프라인 포함)"
          value={searchTerm}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setIsOpen(true);
          }}
          onFocus={() => {
            if (isSearching) setIsOpen(true);
          }}
          className="w-full p-4 pl-12 pr-10 bg-[#0e0e10] border border-gray-700 rounded-2xl text-white placeholder-gray-400 focus:outline-none focus:border-[#00FFA3] focus:ring-2 focus:ring-[#00FFA3]/20 shadow-xl text-sm transition-all"
        />
        <svg
          className="w-5 h-5 text-gray-400 absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
        {searchTerm && (
          <button
            type="button"
            onClick={() => {
              setSearchTerm('');
              setIsOpen(false);
            }}
            className="absolute right-4 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white text-sm font-bold p-1"
            aria-label="검색어 지우기"
          >
            ✕
          </button>
        )}
      </div>

      {/* 플로팅 자동완성 검색 결과창 */}
      {isOpen && isSearching && (
        <div className="absolute left-0 right-0 top-full mt-2 bg-[#161619] border border-gray-700/80 rounded-2xl shadow-2xl z-50 overflow-hidden backdrop-blur-xl animate-in fade-in duration-150 max-h-96 overflow-y-auto">
          {isLoading ? (
            <div className="py-8 text-center text-gray-400 text-xs flex items-center justify-center gap-2">
              <div className="w-4 h-4 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
              스트리머를 검색하는 중...
            </div>
          ) : searchResults.length === 0 ? (
            <div className="py-8 text-center text-gray-400 text-xs">
              '{trimmed}'에 대한 검색 결과가 없습니다.
            </div>
          ) : (
            <div className="divide-y divide-gray-800/60">
              <div className="px-4 py-2 bg-[#121214] text-[11px] font-bold text-gray-400 uppercase tracking-wider flex justify-between items-center">
                <span>검색 결과</span>
                <span className="text-[#00FFA3]">{searchResults.length}명</span>
              </div>
              {searchResults.slice(0, 10).map((stream) => {
                const isStreaming = stream.status === 'LIVE' || stream.status === 'ANALYZING';
                return (
                  <div
                    key={stream.streamId}
                    onClick={() => handleSelectStreamer(stream.streamId)}
                    className="p-3 sm:px-4 flex items-center justify-between hover:bg-[#1a2c22] cursor-pointer transition-colors group"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="relative shrink-0">
                        <img
                          src={stream.profileImageUrl || '/cheese-pick-logo.png'}
                          alt={stream.streamerName}
                          className="w-9 h-9 rounded-full object-cover border border-gray-700 group-hover:border-[#00FFA3]/50 transition-colors"
                        />
                        {isStreaming && (
                          <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-red-500 border-2 border-[#161619] rounded-full" />
                        )}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="font-extrabold text-sm text-white group-hover:text-[#00FFA3] transition-colors truncate">
                            {stream.streamerName}
                          </p>
                          {stream.categoryName && (
                            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-gray-800 text-gray-300 truncate max-w-[100px]">
                              {stream.categoryName}
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-400 group-hover:text-gray-300 truncate max-w-xs">
                          {stream.liveTitle || '최근 방송 기록 없음'}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2.5 shrink-0 pl-3">
                      {isStreaming ? (
                        <div className="text-right">
                          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-red-500/15 text-red-400 border border-red-500/30">
                            <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
                            LIVE
                          </span>
                          {(stream.concurrentUserCount ?? 0) > 0 && (
                            <p className="text-[11px] font-bold text-[#00FFA3] mt-0.5">
                              {(stream.concurrentUserCount ?? 0).toLocaleString()}명
                            </p>
                          )}
                        </div>
                      ) : (
                        <span className="text-[10px] font-bold text-gray-400 px-2 py-0.5 rounded-full bg-gray-800/80">
                          OFFLINE
                        </span>
                      )}
                      <span className="text-gray-500 group-hover:text-[#00FFA3] text-xs font-bold transition-transform group-hover:translate-x-0.5">
                        →
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
