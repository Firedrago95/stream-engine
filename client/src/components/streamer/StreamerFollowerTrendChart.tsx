import React, { useState, useEffect, useMemo } from 'react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';

interface FollowerTrendItem {
  date: string;
  followerCount: number;
  followerGrowth: number;
}

interface StreamerFollowerTrendChartProps {
  channelId: string;
}

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const StreamerFollowerTrendChart: React.FC<StreamerFollowerTrendChartProps> = ({ channelId }) => {
  const [days, setDays] = useState<30 | 90>(30);
  const [viewMode, setViewMode] = useState<'TOTAL' | 'GROWTH'>('TOTAL');
  const [data, setData] = useState<FollowerTrendItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!channelId) return;

    setLoading(true);
    setError(null);

    fetch(`${API_BASE_URL}/api/v1/streamers/${channelId}/follower-trend?days=${days}`)
      .then((res) => {
        if (!res.ok) throw new Error('팔로워 추이 데이터를 불러오는데 실패했습니다.');
        return res.json();
      })
      .then((items: FollowerTrendItem[]) => {
        setData(items || []);
        setLoading(false);
      })
      .catch((err) => {
        console.error('팔로워 트렌드 조회 실패', err);
        setError(err.message);
        setLoading(false);
      });
  }, [channelId, days]);

  const summary = useMemo(() => {
    if (data.length === 0) return null;
    const latest = data[data.length - 1];
    const earliest = data[0];
    const totalGrowth = latest.followerCount - earliest.followerCount;
    const dailyAvg = Math.round(totalGrowth / Math.max(data.length - 1, 1));
    return {
      currentCount: latest.followerCount,
      totalGrowth,
      dailyAvg,
    };
  }, [data]);

  const formatDateLabel = (dateStr: string) => {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      return `${parts[1]}.${parts[2]}`;
    }
    return dateStr;
  };

  const formatNumber = (num: number) => {
    return num.toLocaleString();
  };

  return (
    <div className="p-4 sm:p-6 bg-[#141416] border border-[#2A2A2C] rounded-2xl space-y-4 sm:space-y-5">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-2 border-b border-gray-800/80">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="text-xl">📈</span>
            <h2 className="text-lg font-black text-white tracking-tight">팔로워 성장 추이</h2>
            <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-[#00FFA3]/10 text-[#00FFA3] border border-[#00FFA3]/20">
              일일 스냅샷
            </span>
          </div>
          <p className="text-xs text-gray-100 mt-1">
            매일 새벽 03:30 수집 기준 마감 팔로워 히스토리
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex p-1 bg-[#1A1A1E] border border-gray-800 rounded-xl text-xs font-bold">
            <button
              onClick={() => setViewMode('TOTAL')}
              className={`px-3 py-1 rounded-lg transition-colors ${
                viewMode === 'TOTAL'
                  ? 'bg-gradient-to-r from-[#00FFA3] to-[#00D182] text-black shadow-lg shadow-[#00FFA3]/20'
                  : 'text-gray-100 hover:text-white'
              }`}
            >
              총 팔로워
            </button>
            <button
              onClick={() => setViewMode('GROWTH')}
              className={`px-3 py-1 rounded-lg transition-colors ${
                viewMode === 'GROWTH'
                  ? 'bg-gradient-to-r from-[#00FFA3] to-[#00D182] text-black shadow-lg shadow-[#00FFA3]/20'
                  : 'text-gray-100 hover:text-white'
              }`}
            >
              일일 순증
            </button>
          </div>

          <div className="inline-flex p-1 bg-[#1A1A1E] border border-gray-800 rounded-xl text-xs font-bold">
            <button
              onClick={() => setDays(30)}
              className={`px-3 py-1 rounded-lg transition-colors ${
                days === 30
                  ? 'bg-gray-700 text-white shadow'
                  : 'text-gray-100 hover:text-white'
              }`}
            >
              30일
            </button>
            <button
              onClick={() => setDays(90)}
              className={`px-3 py-1 rounded-lg transition-colors ${
                days === 90
                  ? 'bg-gray-700 text-white shadow'
                  : 'text-gray-100 hover:text-white'
              }`}
            >
              90일
            </button>
          </div>
        </div>
      </div>

      {summary && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 p-4 bg-[#1A1A1E] border border-gray-800/60 rounded-xl">
          <div>
            <p className="text-[11px] text-gray-100 font-medium">최근 스냅샷 팔로워</p>
            <p className="text-lg sm:text-xl font-black text-white font-mono mt-0.5">
              {formatNumber(summary.currentCount)}
              <span className="text-xs text-gray-100 font-normal ml-1">명</span>
            </p>
          </div>
          <div>
            <p className="text-[11px] text-gray-100 font-medium">{days}일간 총 순증</p>
            <p
              className={`text-lg sm:text-xl font-black font-mono mt-0.5 ${
                summary.totalGrowth >= 0 ? 'text-[#00FFA3]' : 'text-rose-400'
              }`}
            >
              {summary.totalGrowth >= 0
                ? `+${formatNumber(summary.totalGrowth)}`
                : formatNumber(summary.totalGrowth)}
              <span className="text-xs text-gray-100 font-normal ml-1">명</span>
            </p>
          </div>
          <div className="col-span-2 sm:col-span-1">
            <p className="text-[11px] text-gray-100 font-medium">하루 평균 성장</p>
            <p
              className={`text-lg sm:text-xl font-black font-mono mt-0.5 ${
                summary.dailyAvg >= 0 ? 'text-[#67BFFF]' : 'text-rose-400'
              }`}
            >
              {summary.dailyAvg >= 0
                ? `+${formatNumber(summary.dailyAvg)}`
                : formatNumber(summary.dailyAvg)}
              <span className="text-xs text-gray-100 font-normal ml-1">명/일</span>
            </p>
          </div>
        </div>
      )}

      {loading ? (
        <div className="h-64 flex flex-col items-center justify-center gap-3">
          <div className="w-8 h-8 border-3 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
          <p className="text-xs text-gray-100">팔로워 데이터를 불러오는 중입니다...</p>
        </div>
      ) : error ? (
        <div className="h-64 flex items-center justify-center text-xs text-rose-400 font-medium">
          {error}
        </div>
      ) : data.length === 0 ? (
        <div className="h-64 flex flex-col items-center justify-center gap-2 text-center text-gray-100">
          <span className="text-2xl">🌱</span>
          <p className="text-sm font-bold text-gray-100">아직 수집된 팔로워 히스토리가 없습니다.</p>
          <p className="text-xs text-gray-200">
            매일 새벽 03:30 정기 수집기를 통해 일일 스냅샷이 누적됩니다.
          </p>
        </div>
      ) : (
        <div className="h-72 w-full pt-2">
          <ResponsiveContainer width="100%" height="100%">
            {viewMode === 'TOTAL' ? (
              <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="followerGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.35} />
                    <stop offset="95%" stopColor="#00FFA3" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#262628" vertical={false} />
                <XAxis
                  dataKey="date"
                  tickFormatter={formatDateLabel}
                  stroke="#52525B"
                  tick={{ fill: '#E4E4E7', fontSize: 10 }}
                  tickLine={false}
                  axisLine={{ stroke: '#27272A' }}
                  minTickGap={20}
                />
                <YAxis
                  width={42}
                  stroke="#52525B"
                  tick={{ fill: '#E4E4E7', fontSize: 10 }}
                  tickLine={false}
                  axisLine={false}
                  domain={['auto', 'auto']}
                  tickFormatter={(val) => `${(val / 1000).toFixed(val >= 10000 ? 0 : 1)}k`}
                />
                <Tooltip
                  content={({ active, payload }) => {
                    if (active && payload && payload.length) {
                      const item = payload[0].payload as FollowerTrendItem;
                      return (
                        <div className="p-3 bg-[#1A1A1E] border border-gray-700 rounded-xl shadow-2xl text-xs space-y-1">
                          <p className="text-gray-200 font-mono">{item.date}</p>
                          <p className="text-white font-bold text-sm font-mono">
                            {formatNumber(item.followerCount)}명
                          </p>
                          <p
                            className={`font-mono font-bold ${
                              item.followerGrowth >= 0 ? 'text-[#00FFA3]' : 'text-rose-400'
                            }`}
                          >
                            전일 대비 {item.followerGrowth >= 0 ? `+${formatNumber(item.followerGrowth)}` : formatNumber(item.followerGrowth)}명
                          </p>
                        </div>
                      );
                    }
                    return null;
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="followerCount"
                  stroke="#00FFA3"
                  strokeWidth={2.5}
                  fillOpacity={1}
                  fill="url(#followerGradient)"
                />
              </AreaChart>
            ) : (
              <BarChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#262628" vertical={false} />
                <XAxis
                  dataKey="date"
                  tickFormatter={formatDateLabel}
                  stroke="#52525B"
                  tick={{ fill: '#E4E4E7', fontSize: 10 }}
                  tickLine={false}
                  axisLine={{ stroke: '#27272A' }}
                  minTickGap={20}
                />
                <YAxis
                  width={42}
                  stroke="#52525B"
                  tick={{ fill: '#E4E4E7', fontSize: 10 }}
                  tickLine={false}
                  axisLine={false}
                  domain={['auto', 'auto']}
                  tickFormatter={(val) => `${val >= 0 ? '+' : ''}${val}`}
                />
                <Tooltip
                  content={({ active, payload }) => {
                    if (active && payload && payload.length) {
                      const item = payload[0].payload as FollowerTrendItem;
                      return (
                        <div className="p-3 bg-[#1A1A1E] border border-gray-700 rounded-xl shadow-2xl text-xs space-y-1">
                          <p className="text-gray-200 font-mono">{item.date}</p>
                          <p
                            className={`font-mono font-bold text-sm ${
                              item.followerGrowth >= 0 ? 'text-[#00FFA3]' : 'text-rose-400'
                            }`}
                          >
                            일일 증감 {item.followerGrowth >= 0 ? `+${formatNumber(item.followerGrowth)}` : formatNumber(item.followerGrowth)}명
                          </p>
                          <p className="text-gray-200 font-mono">
                            누적 {formatNumber(item.followerCount)}명
                          </p>
                        </div>
                      );
                    }
                    return null;
                  }}
                />
                <Bar dataKey="followerGrowth" radius={[4, 4, 0, 0]}>
                  {data.map((entry, index) => (
                    <Cell
                      key={`cell-${index}`}
                      fill={entry.followerGrowth >= 0 ? '#00FFA3' : '#F43F5E'}
                    />
                  ))}
                </Bar>
              </BarChart>
            )}
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
};
