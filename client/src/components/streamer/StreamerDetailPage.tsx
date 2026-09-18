import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { StreamerCalendarTimeline } from './StreamerCalendarTimeline';
import { StreamerCategories, type CategoryData } from './StreamerCategories';
import { StreamerSessionList, type SessionItemData } from './StreamerSessionList';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

interface ProfileData {
  header: {
    channelId: string;
    streamerName: string;
    profileImageUrl: string;
    isLive: boolean;
    currentFollowers: number;
    followerGrowth7d: number;
    followerGrowth30d: number;
  };
  summary: {
    averageViewers: number;
    peakViewers: number;
    totalBroadcastDurationSeconds: number;
    hoursWatched: number;
    followerGrowth30d: number;
    broadcastDays30d?: number;
    attendanceRate30d?: number;
  };
  mostPlayedCategories: CategoryData[];
}

const formatHours = (seconds: number) => {
  const hours = Math.round((seconds / 3600) * 10) / 10;
  return `${hours.toLocaleString()}시간`;
};

export const StreamerDetailPage: React.FC = () => {
  const { channelId } = useParams<{ channelId: string }>();
  const navigate = useNavigate();

  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);

  const [sessions, setSessions] = useState<SessionItemData[]>([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionTotalPages, setSessionTotalPages] = useState(0);
  const [sessionTotalElements, setSessionTotalElements] = useState(0);
  const [sessionsLoading, setSessionsLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!channelId) return;

    setProfileLoading(true);
    fetch(`${API_BASE_URL}/api/v1/streamers/${channelId}/profile`)
      .then((res) => {
        if (!res.ok) throw new Error('프로필 정보를 불러오는데 실패했습니다.');
        return res.json();
      })
      .then((data) => {
        setProfile(data);
        setProfileLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setProfileLoading(false);
      });
  }, [channelId]);

  useEffect(() => {
    if (!channelId) return;

    setSessionsLoading(true);
    fetch(`${API_BASE_URL}/api/v1/streamers/${channelId}/sessions?page=${sessionPage}&size=10`)
      .then((res) => res.json())
      .then((data) => {
        setSessions(data.sessions || []);
        setSessionTotalPages(data.totalPages || 0);
        setSessionTotalElements(data.totalElements || 0);
        setSessionsLoading(false);
      })
      .catch((err) => {
        console.error('세션 전적 조회 실패', err);
        setSessionsLoading(false);
      });
  }, [channelId, sessionPage]);

  if (error) {
    return (
      <div className="p-12 text-center">
        <p className="text-rose-500 font-bold mb-4">{error}</p>
        <button
          onClick={() => navigate('/streamers')}
          className="px-4 py-2 bg-[#1a1a1c] border border-gray-800 text-gray-300 rounded-lg text-sm hover:text-white"
        >
          ← 스트리머 목록으로 돌아가기
        </button>
      </div>
    );
  }

  if (profileLoading || !profile) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[50vh] gap-4">
        <div className="w-10 h-10 border-3 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
        <div className="text-gray-300 text-sm font-medium">스트리머 통계 데이터를 로딩 중입니다...</div>
      </div>
    );
  }

  const { header, summary, mostPlayedCategories } = profile;

  return (
    <div className="space-y-6 sm:space-y-8 pb-16">
      <button
        onClick={() => navigate('/streamers')}
        className="inline-flex items-center gap-2 text-xs font-bold text-gray-300 hover:text-[#00FFA3] transition-colors"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        스트리머 통계 리더보드로 이동
      </button>

      <div className="p-6 sm:p-8 bg-[#141416] border border-[#2A2A2C] rounded-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6 relative overflow-hidden">
        <div className="flex items-center gap-5">
          <div className="relative">
            <img
              src={header.profileImageUrl || '/cheese-pick-logo.png'}
              alt={header.streamerName}
              className="w-20 h-20 sm:w-24 sm:h-24 rounded-full object-cover border-2 border-gray-800 shadow-xl"
            />
            {header.isLive && (
              <span className="absolute bottom-0 right-0 px-2 py-0.5 rounded-full text-[10px] font-black bg-red-500 text-white border-2 border-[#141416] shadow animate-pulse">
                LIVE
              </span>
            )}
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                {header.streamerName}
              </h1>
              {header.isLive && (
                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-red-500/15 text-red-400 border border-red-500/30">
                  <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-ping" />
                  방송 중
                </span>
              )}
            </div>

            <div className="flex flex-wrap items-center gap-3 text-xs text-gray-300 font-mono">
              <span>
                총 팔로워 <strong className="text-white font-bold">{header.currentFollowers.toLocaleString()}명</strong>
              </span>
              <span className="text-gray-500">|</span>
              <span className="flex items-center gap-1">
                최근 7일{' '}
                <strong className={header.followerGrowth7d >= 0 ? 'text-[#00FFA3] font-bold' : 'text-rose-400 font-bold'}>
                  {header.followerGrowth7d >= 0 ? `+${header.followerGrowth7d}` : header.followerGrowth7d}
                </strong>
              </span>
              <span className="text-gray-500">|</span>
              <span className="flex items-center gap-1">
                최근 30일{' '}
                <strong className={header.followerGrowth30d >= 0 ? 'text-[#00FFA3] font-bold' : 'text-rose-400 font-bold'}>
                  {header.followerGrowth30d >= 0 ? `+${header.followerGrowth30d}` : header.followerGrowth30d}
                </strong>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-4">
        <div className="p-4 sm:p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
          <p className="text-xs text-gray-200 font-bold mb-1 flex items-center gap-1">
            <span>👥</span> 평균 시청자
          </p>
          <p className="text-xl sm:text-2xl font-black text-[#67BFFF] font-mono">
            {summary.averageViewers.toLocaleString()}
            <span className="text-xs text-gray-400 font-normal ml-1">명</span>
          </p>
          <p className="text-[10px] text-gray-400 mt-1">최근 30일 가중 평균</p>
        </div>

        <div className="p-4 sm:p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
          <p className="text-xs text-gray-200 font-bold mb-1 flex items-center gap-1">
            <span>🚀</span> 최고 시청자
          </p>
          <p className="text-xl sm:text-2xl font-black text-[#A78BFA] font-mono">
            {summary.peakViewers.toLocaleString()}
            <span className="text-xs text-gray-400 font-normal ml-1">명</span>
          </p>
          <p className="text-[10px] text-gray-400 mt-1">최근 30일 순간 피크</p>
        </div>

        <div className="p-4 sm:p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
          <p className="text-xs text-gray-200 font-bold mb-1 flex items-center gap-1">
            <span>⏱️</span> 총 방송 시간
          </p>
          <p className="text-xl sm:text-2xl font-black text-white font-mono">
            {formatHours(summary.totalBroadcastDurationSeconds)}
          </p>
          <p className="text-[10px] text-gray-400 mt-1">최근 30일 누적 라이브</p>
        </div>

        <div className="p-4 sm:p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
          <p className="text-xs text-gray-200 font-bold mb-1 flex items-center gap-1">
            <span>🌱</span> 방송 성실도
          </p>
          <p className="text-xl sm:text-2xl font-black text-[#00FFA3] font-mono">
            {summary.broadcastDays30d ?? 0}
            <span className="text-xs text-gray-300 font-normal ml-1">일</span>
            <span className="text-sm text-gray-400 font-normal ml-2">({summary.attendanceRate30d ?? 0}%)</span>
          </p>
          <p className="text-[10px] text-gray-400 mt-1">최근 30일 방송 출석률</p>
        </div>

        <div className="p-4 sm:p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl col-span-2 lg:col-span-1">
          <p className="text-xs text-gray-200 font-bold mb-1 flex items-center gap-1">
            <span>📈</span> 팔로워 순증
          </p>
          <p className={`text-xl sm:text-2xl font-black font-mono ${summary.followerGrowth30d >= 0 ? 'text-[#00FFA3]' : 'text-rose-400'}`}>
            {summary.followerGrowth30d >= 0 ? `+${summary.followerGrowth30d.toLocaleString()}` : summary.followerGrowth30d.toLocaleString()}
            <span className="text-xs text-gray-400 font-normal ml-1">명</span>
          </p>
          <p className="text-[10px] text-gray-400 mt-1">최근 30일간 성장</p>
        </div>
      </div>

      <StreamerCalendarTimeline channelId={channelId!} />

      <StreamerCategories categories={mostPlayedCategories} />

      <StreamerSessionList
        sessions={sessions}
        page={sessionPage}
        totalPages={sessionTotalPages}
        totalElements={sessionTotalElements}
        onPageChange={setSessionPage}
        isLoading={sessionsLoading}
      />
    </div>
  );
};
