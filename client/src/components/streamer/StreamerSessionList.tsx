import React from 'react';

export interface SessionItemData {
  sessionId: string;
  title: string;
  categoryName: string;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number;
  peakViewers: number;
  avgViewers: number;
  followerGrowth: number | null;
  subscriberChatRatio: number | null;
  vodUrl: string | null;
}

interface StreamerSessionListProps {
  sessions: SessionItemData[];
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (newPage: number) => void;
  onSessionClick?: (sessionId: string) => void;
  isLoading: boolean;
}

const formatDuration = (seconds: number) => {
  if (!seconds || seconds <= 0) return '0분';
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) {
    return `${hours}시간 ${minutes}분`;
  }
  return `${minutes}분`;
};

const formatDate = (isoString: string) => {
  try {
    const d = new Date(isoString);
    const m = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    const hours = d.getHours().toString().padStart(2, '0');
    const mins = d.getMinutes().toString().padStart(2, '0');
    return `${m}.${day} ${hours}:${mins}`;
  } catch (e) {
    return '--';
  }
};

export const StreamerSessionList: React.FC<StreamerSessionListProps> = ({
  sessions,
  page,
  totalPages,
  totalElements,
  onPageChange,
  onSessionClick,
  isLoading,
}) => {
  if (isLoading) {
    return (
      <div className="p-8 bg-[#141416] border border-[#2A2A2C] rounded-2xl text-center text-gray-200 font-medium">
        <div className="w-8 h-8 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin mx-auto mb-3" />
        전적 데이터를 불러오는 중...
      </div>
    );
  }

  if (!sessions || sessions.length === 0) {
    return (
      <div className="p-8 bg-[#141416] border border-[#2A2A2C] rounded-2xl text-center text-gray-200 font-medium text-sm">
        과거 방송 세션 기록이 없습니다.
      </div>
    );
  }

  return (
    <div className="p-5 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
      <div className="flex items-center justify-between mb-5">
        <h3 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
          <span>📜</span> 방송 세션 히스토리
        </h3>
        <span className="text-xs text-gray-300 font-mono font-semibold">
          총 {totalElements.toLocaleString()}개 방송
        </span>
      </div>

      <div className="space-y-3">
        {sessions.map((sess) => (
          <div
            key={sess.sessionId}
            onClick={() => onSessionClick?.(sess.sessionId)}
            onKeyDown={(event) => {
              if (onSessionClick && (event.key === 'Enter' || event.key === ' ')) {
                event.preventDefault();
                onSessionClick(sess.sessionId);
              }
            }}
            role={onSessionClick ? 'button' : undefined}
            tabIndex={onSessionClick ? 0 : undefined}
            className="p-4 bg-[#0e0e10] border border-gray-800/80 rounded-xl hover:border-gray-700 transition-all flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
          >
            <div className="space-y-1.5 flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono text-gray-300">
                  {formatDate(sess.startedAt)}
                </span>
                <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-[#1e1e24] text-[#67BFFF] border border-gray-800 truncate">
                  {sess.categoryName || '기타'}
                </span>
                {!sess.endedAt && (
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-red-500/10 text-red-400 border border-red-500/20 animate-pulse">
                    LIVE
                  </span>
                )}
              </div>
              <h4 className="text-sm font-bold text-white truncate group-hover:text-[#00FFA3] transition-colors">
                {sess.title || '제목 없음'}
              </h4>
            </div>

            <div className="flex items-center gap-4 sm:gap-6 self-end sm:self-auto shrink-0 text-xs font-mono">
              <div className="text-right">
                <p className="text-[10.5px] text-gray-300 font-medium">방송 시간</p>
                <p className="font-bold text-gray-100">{formatDuration(sess.durationSeconds)}</p>
              </div>

              <div className="text-right">
                <p className="text-[10.5px] text-gray-300 font-medium">평균 / 최고</p>
                <p className="font-bold text-gray-100">
                  <span className="text-[#67BFFF]">{sess.avgViewers.toLocaleString()}</span> /{' '}
                  <span className="text-[#A78BFA]">{sess.peakViewers.toLocaleString()}</span>
                </p>
              </div>

              {sess.followerGrowth !== null && sess.followerGrowth !== undefined && (
                <div className="text-right">
                  <p className="text-[10.5px] text-gray-300 font-medium">팔로워</p>
                  <p className={`font-bold ${sess.followerGrowth >= 0 ? 'text-[#00FFA3]' : 'text-rose-400'}`}>
                    {sess.followerGrowth >= 0 ? `+${sess.followerGrowth}` : sess.followerGrowth}
                  </p>
                </div>
              )}

              {sess.subscriberChatRatio !== null && sess.subscriberChatRatio !== undefined && (
                <div className="text-right">
                  <p className="text-[10.5px] text-gray-300 font-medium">구독자 채팅</p>
                  <p className="font-bold text-[#00FFA3]">{sess.subscriberChatRatio.toFixed(1)}%</p>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-6 pt-4 border-t border-gray-800/60 text-xs">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 0}
            className="px-3 py-1.5 rounded-lg bg-[#1a1a1c] border border-gray-800 text-gray-300 disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-800 transition-colors font-bold"
          >
            이전
          </button>
          <span className="text-gray-200 font-mono font-semibold">
            {page + 1} / {totalPages}
          </span>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            className="px-3 py-1.5 rounded-lg bg-[#1a1a1c] border border-gray-800 text-gray-300 disabled:opacity-30 disabled:cursor-not-allowed hover:bg-gray-800 transition-colors font-bold"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
};
