import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();
  const { analysisData, isLoading, error } = useStreamAnalysis(streamId || '');

  const chartData = analysisData?.dataPoints || [];

  // 5초 주기라면 약 1분 15초 동안의 변화를 보여줍니다.
  const displayData = chartData.slice(-20);

  const formatXAxis = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString('ko-KR', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
  };

  if (!streamId) return <div className="p-10 text-center text-gray-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full max-w-7xl mx-auto">
        <div className="mb-6 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-2 bg-gray-800 border border-gray-700 rounded-lg hover:bg-gray-700 text-gray-400">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
          </button>
          <h2 className="text-2xl font-bold text-gray-100">실시간 화력 분석 <span className="text-sm font-normal text-gray-500 ml-2">ID: {streamId}</span></h2>
        </div>

        {(isLoading || chartData.length === 0) && !error ? (
            <div className="h-64 flex items-center justify-center text-gray-500 bg-gray-800/50 rounded-xl border border-gray-700 animate-pulse">
              분석 데이터를 기다리는 중...
            </div>
        ) : (
            <div className="space-y-6">
              <div className="p-5 bg-gray-800 border border-gray-700 rounded-xl shadow-lg">
                {/* 타이틀에서 개수 안내 수정 */}
                <h3 className="text-lg font-semibold text-gray-100 mb-6">최근 채팅 화력 트렌드</h3>
                <div className="h-[400px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={displayData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <defs>
                        <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#00FFA3" stopOpacity={0.3}/>
                          <stop offset="95%" stopColor="#00FFA3" stopOpacity={0}/>
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} />
                      <XAxis
                          dataKey="timestamp"
                          tickFormatter={formatXAxis}
                          stroke="#666"
                          fontSize={11}
                          tickMargin={12}
                          /* 💡 틱 간격 강제 조정: 시간끼리 겹치지 않게 보장 */
                          minTickGap={50}
                      />
                      <YAxis stroke="#666" fontSize={11} tickMargin={10} />
                      <Tooltip
                          labelFormatter={(value) => `시간: ${formatXAxis(value)}`}
                          contentStyle={{ backgroundColor: '#1a1a1c', border: '1px solid #333', borderRadius: '8px', color: '#fff' }}
                      />
                      <Area
                          type="monotone"
                          dataKey="value"
                          stroke="#00FFA3"
                          strokeWidth={3}
                          fill="url(#colorValue)"
                          animationDuration={500}
                          isAnimationActive={true}
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
