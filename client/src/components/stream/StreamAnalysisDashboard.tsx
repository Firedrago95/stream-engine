import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

// 💡 템플릿 톤앤매너에 맞춘 세련된 펄스(Pulse) 스켈레톤
const DashboardSkeleton = () => (
    <div className="w-full h-[400px] border border-slate-200 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-800 animate-pulse flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="w-10 h-10 border-4 border-[#00FFA3] border-t-transparent rounded-full animate-spin" />
        <p className="text-slate-400 text-sm font-medium">실시간 데이터 분석 중...</p>
      </div>
    </div>
);

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  // 💡 기존 백엔드 연동 로직 100% 유지
  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(streamId || '');
  const chartData = analysisData?.dataPoints || [];

  if (!streamId) {
    return <div className="p-10 text-center text-slate-400">잘못된 접근입니다.</div>;
  }

  // 차트 마지막 데이터 기반 현재 화력 계산 (예시)
  const currentChatCount = chartData.length > 0 ? chartData[chartData.length - 1].chatCount : 0;

  return (
      <div className="w-full max-w-7xl mx-auto">
        {/* 헤더 및 뒤로가기 */}
        <div className="mb-8 sm:flex sm:justify-between sm:items-center">
          <div className="flex items-center gap-3">
            <button
                onClick={() => navigate(-1)}
                className="p-2 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors text-slate-500 dark:text-slate-400"
            >
              <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
            </button>
            <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100">
              실시간 채팅 화력 <span className="text-sm font-normal text-slate-500 dark:text-slate-400 ml-2">ID: {streamId}</span>
            </h2>
          </div>
        </div>

        {/* 상태별 UI 렌더링 */}
        {(isLoading || isGathering || chartData.length === 0) && !error ? (
            <DashboardSkeleton />
        ) : error ? (
            <div className="h-64 flex flex-col items-center justify-center text-rose-500 border border-rose-500/20 rounded-xl bg-rose-500/5">
              <p className="font-semibold mb-1">데이터를 불러올 수 없습니다.</p>
              <p className="text-sm">{error}</p>
            </div>
        ) : (
            <div className="space-y-6">
              {/* 💡 상단 요약 카드 (Stat Cards) - Mosaic 스타일 */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-white dark:bg-slate-800 p-5 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm">
                  <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">현재 채팅 화력</h3>
                  <div className="text-3xl font-bold text-slate-800 dark:text-slate-100">
                    {currentChatCount} <span className="text-sm font-medium text-slate-500">회/분</span>
                  </div>
                </div>
                {/* 필요시 추가 스탯 카드를 여기에 배치합니다 */}
                <div className="bg-white dark:bg-slate-800 p-5 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm">
                  <h3 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">분석 데이터 수집량</h3>
                  <div className="text-3xl font-bold text-slate-800 dark:text-slate-100">
                    {chartData.length} <span className="text-sm font-medium text-slate-500">포인트</span>
                  </div>
                </div>
              </div>

              {/* 💡 차트 영역 - 모던한 트레이딩뷰 스타일로 개편 */}
              <div className="p-5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl shadow-sm">
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100 mb-4">화력 트렌드</h2>
                <div className="h-[400px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <defs>
                        <linearGradient id="colorChat" x1="0" y1="0" x2="0" y2="1">
                          {/* 치지직 연두색(#00FFA3) 그라데이션 */}
                          <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3}/>
                          <stop offset="95%" stopColor="#00FFA3" stopOpacity={0}/>
                        </linearGradient>
                      </defs>
                      {/* 은은한 다크모드 그리드 */}
                      <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} opacity={0.5} />
                      <XAxis
                          dataKey="timestamp"
                          stroke="#475569"
                          tick={{ fill: '#94a3b8', fontSize: 11 }}
                          tickMargin={10}
                          tickLine={false}
                          axisLine={false}
                      />
                      <YAxis
                          stroke="#475569"
                          tick={{ fill: '#94a3b8', fontSize: 11 }}
                          tickMargin={10}
                          tickLine={false}
                          axisLine={false}
                      />
                      {/* 고급스러운 다크모드 툴팁 */}
                      <Tooltip
                          contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px', color: '#f8fafc', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                          itemStyle={{ color: '#00FFA3', fontWeight: '600' }}
                          labelStyle={{ color: '#94a3b8', marginBottom: '4px', fontSize: '12px' }}
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
              </div>
            </div>
        )}
      </div>
  );
};
