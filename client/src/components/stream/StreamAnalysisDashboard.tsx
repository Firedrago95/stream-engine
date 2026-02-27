// client/src/components/stream/StreamAnalysisDashboard.tsx
import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

const DashboardSkeleton = () => (
    <div className="h-96 border border-white/10 rounded-xl flex items-center justify-center bg-white/5 animate-pulse">
      <div className="flex flex-col items-center gap-4">
        <div className="w-12 h-12 border-4 border-chzzk-green border-t-transparent rounded-full animate-spin" />
        <p className="text-chzzk-green font-medium">채팅 화력 분석 중...</p>
      </div>
    </div>
);

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  // 💡 1. 훅의 반환값 이름(analysisData, isGathering)을 정확히 맞춥니다.
  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(streamId || '');

  // 💡 2. 데이터가 null일 경우를 대비해 빈 배열([])을 기본값으로 줍니다.
  const chartData = analysisData || [];

  // 💡 3. streamId가 아예 없으면 조기 종료합니다.
  if (!streamId) {
    return <div className="p-10 text-center text-white">잘못된 접근입니다.</div>;
  }

  return (
      <div className="max-w-[1800px] mx-auto p-6 md:p-10">
        {/* 헤더 및 뒤로가기 */}
        <div className="flex items-center gap-4 mb-8">
          <button
              onClick={() => navigate(-1)}
              className="p-2 hover:bg-white/10 rounded-lg transition-colors text-gray-400 hover:text-white"
              aria-label="목록으로 돌아가기"
          >
            ← 뒤로가기
          </button>
          <h2 className="text-2xl font-bold text-white">
            실시간 채팅 화력 <span className="text-chzzk-green font-normal text-lg ml-2">ID: {streamId}</span>
          </h2>
        </div>

        {/* 상태별 UI 렌더링 */}
        {/* 💡 4. isLoading뿐만 아니라 수집 중(isGathering)이거나 차트 데이터가 없을 때 멋진 스켈레톤을 보여줍니다. */}
        {(isLoading || isGathering || chartData.length === 0) && !error ? (
            <DashboardSkeleton />
        ) : error ? (
            <div className="h-96 flex flex-col items-center justify-center text-red-400 border border-red-400/20 rounded-xl bg-red-400/5">
              <p className="font-bold mb-2">데이터를 불러올 수 없습니다.</p>
              <p className="text-sm">{error}</p>
            </div>
        ) : (
            <div className="h-[500px] w-full p-6 border border-white/10 rounded-xl bg-[#141517] shadow-xl">
              <ResponsiveContainer width="100%" height="100%">
                {/* 💡 5. analysisData(chartData)를 차트에 주입합니다. */}
                <AreaChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorChat" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.8}/>
                      <stop offset="95%" stopColor="#00FFA3" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#2A2A2A" vertical={false} />
                  <XAxis
                      dataKey="timestamp"
                      stroke="#666"
                      tick={{ fill: '#888', fontSize: 12 }}
                      tickMargin={10}
                  />
                  <YAxis
                      stroke="#666"
                      tick={{ fill: '#888', fontSize: 12 }}
                      tickMargin={10}
                      axisLine={false}
                      tickLine={false}
                  />
                  <Tooltip
                      contentStyle={{ backgroundColor: '#1E1F22', border: '1px solid #333', borderRadius: '8px', color: '#fff' }}
                      itemStyle={{ color: '#00FFA3', fontWeight: 'bold' }}
                      labelStyle={{ color: '#aaa', marginBottom: '4px' }}
                  />
                  <Area
                      type="monotone"
                      dataKey="chatCount"
                      name="채팅 수"
                      stroke="#00FFA3"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#colorChat)"
                      animationDuration={1000}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
        )}
      </div>
  );
};
