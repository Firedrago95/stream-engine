import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const API_BASE_URL = import.meta.env.VITE_API_URL || '';

export interface RecentHighlightSession {
  streamId: string;
  streamerName: string;
  sessionId: string;
  title: string;
  categoryName: string;
  startedAt: string;
  endedAt: string | null;
  durationSeconds: number;
  peakViewers: number;
  avgViewers: number;
  highlightCount: number;
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
  } catch {
    return '--';
  }
};

export const HighlightsPage: React.FC = () => {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<RecentHighlightSession[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    const fetchRecentSessions = async () => {
      setIsLoading(true);
      setError(null);
      try {
        const res = await fetch(`${API_BASE_URL}/api/v1/analysis/highlights/recent-sessions?limit=30`);
        if (!res.ok) throw new Error('최근 하이라이트 목록을 불러오지 못했습니다.');
        const data = await res.json();
        if (isMounted) {
          setSessions(data || []);
        }
      } catch (err: any) {
        if (isMounted) {
          setError(err.message || '네트워크 오류가 발생했습니다.');
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    };

    fetchRecentSessions();
    return () => {
      isMounted = false;
    };
  }, []);

  return (
    <div className="space-y-6">
      {/* 상단 헤더 & 설명 배너 */}
      <div className="bg-gradient-to-r from-[#1a1a1c] via-[#141416] to-[#1a1a1c] border border-gray-800 rounded-3xl p-6 sm:p-8 shadow-xl relative overflow-hidden">
        <div className="absolute top-0 right-0 -mt-8 -mr-8 w-48 h-48 bg-[#00FFA3]/5 rounded-full blur-3xl pointer-events-none"></div>
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-2xl sm:text-3xl">🎬</span>
              <h1 className="text-2xl sm:text-3xl font-black text-white italic tracking-tighter">
                방송 하이라이트 피드
              </h1>
              <span className="px-2 py-0.5 text-[10px] font-black uppercase tracking-wider rounded bg-yellow-400/20 text-yellow-300 border border-yellow-400/40">
                AI 분석 완료
              </span>
            </div>
            <p className="text-gray-300 text-xs sm:text-sm font-medium leading-relaxed max-w-2xl">
              방송 종료 후 <span className="text-[#00FFA3] font-bold">자동 분석으로 발굴된 명장면과 클라이맥스 타임라인</span>을 확인해보세요.
            </p>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <span className="text-xs font-mono text-gray-400">
              최근 분석 세션 <strong className="text-white font-bold">{sessions.length}</strong>개
            </span>
          </div>
        </div>
      </div>

      {/* 로딩 상태 */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="bg-[#1a1a1c] border border-gray-800 rounded-2xl p-5 animate-pulse space-y-3">
              <div className="h-4 bg-gray-800 rounded w-1/3"></div>
              <div className="h-5 bg-gray-700 rounded w-4/5"></div>
              <div className="h-4 bg-gray-800 rounded w-1/2"></div>
            </div>
          ))}
        </div>
      )}

      {/* 에러 상태 */}
      {error && !isLoading && (
        <div className="p-12 text-center bg-[#1a1a1c] border border-red-500/30 rounded-3xl text-red-400 font-bold">
          {error}
        </div>
      )}

      {/* 목록 렌더링 */}
      {!isLoading && !error && sessions.length === 0 && (
        <div className="p-16 text-center bg-[#1a1a1c] border border-gray-800 rounded-3xl text-gray-300 font-bold flex flex-col items-center gap-3">
          <span className="text-3xl">☕</span>
          <span>최근 분석 완료된 방송 세션이 없습니다.</span>
        </div>
      )}

      {!isLoading && !error && sessions.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {sessions.map((session) => (
            <div
              key={session.sessionId}
              onClick={() => navigate(`/streams/${session.streamId}?sessionId=${encodeURIComponent(session.sessionId)}`)}
              className="group bg-[#141416] hover:bg-[#1a1a1c] border border-gray-800 hover:border-[#00FFA3]/50 rounded-2xl p-5 transition-all duration-200 cursor-pointer shadow-lg flex flex-col justify-between"
            >
              <div>
                {/* 상단 스트리머명 & 카테고리 */}
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="text-xs font-bold text-gray-300 group-hover:text-white transition-colors">
                    {session.streamerName}
                  </span>
                  {session.categoryName && (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-gray-800/80 text-gray-300 border border-gray-700/50">
                      {session.categoryName}
                    </span>
                  )}
                </div>

                {/* 방송 제목 */}
                <h3 className="text-sm sm:text-base font-bold text-white line-clamp-2 leading-snug mb-3 group-hover:text-[#00FFA3] transition-colors">
                  {session.title || '제목 없는 방송'}
                </h3>
              </div>

              {/* 하단 지표 정보 */}
              <div className="pt-3 border-t border-gray-800/60 mt-2 space-y-2">
                <div className="flex items-center justify-between text-xs text-gray-400 font-mono">
                  <span>⏱️ {formatDuration(session.durationSeconds)}</span>
                  <span>👥 최고 {session.peakViewers.toLocaleString()}명</span>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <span className="text-[11px] text-gray-500 font-mono">
                    {formatDate(session.startedAt)}
                  </span>

                  <span className="inline-flex items-center gap-1 text-xs font-black px-2.5 py-1 rounded-lg bg-[#00FFA3]/10 border border-[#00FFA3]/30 text-[#00FFA3] group-hover:bg-[#00FFA3] group-hover:text-black transition-all">
                    <span>🔥</span>
                    <span>{session.highlightCount > 0 ? `${session.highlightCount}개 하이라이트` : '세션 리포트'}</span>
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
export default HighlightsPage;
