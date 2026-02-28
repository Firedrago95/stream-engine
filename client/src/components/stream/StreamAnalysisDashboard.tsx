import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

/**
 * 대시보드 유연한 설정값
 * 나중에 주기를 바꾸고 싶다면 여기 변수들만 수정하면 됩니다.
 */
const CONFIG = {
  POLLING_INTERVAL: 5000,      // 데이터 갱신 주기 (5초)
  DISPLAY_POINTS: 20,          // 화면에 표시할 데이터 개수
  ANIMATION_SAFETY_GAP: 200,   // 다음 데이터 수신 전 애니메이션을 미리 끝낼 여유 시간 (ms)
  CHART_COLOR: "#00FFA3",      // 치지직 포인트 컬러
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  // 💡 설정된 주기에 맞춰 훅 호출
  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

  const chartData = analysisData?.dataPoints || [];

  // 💡 설정된 개수만큼만 슬라이싱 (Sliding Window)
  const displayData = chartData.slice(-CONFIG.DISPLAY_POINTS);

  // 시간 포맷터 (HH:mm:ss)
  const formatXAxis = (timestamp: number) => {
    return new Date(timestamp).toLocaleTimeString('ko-KR', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
  };

  // 💡 애니메이션 지속 시간을 수집 주기와 동기화 (네트워크 지연 고려해 살짝 짧게 설정)
  const dynamicDuration = CONFIG.POLLING_INTERVAL - CONFIG.ANIMATION_SAFETY_GAP;

  if (!streamId) return <div className="p-10 text-center text-gray-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full max-w-7xl mx-auto">
        <div className="mb-6 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-2 bg-gray-800 border border-gray-700 rounded-lg hover:bg-gray-700 text-gray-400 transition-colors">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
          </button>
          <h2 className="text-2xl font-bold text-gray-100">실시간 화력 분석 <span className="text-sm font-normal text-gray-500 ml-2">ID: {streamId}</span></h2>
        </div>

        {(isLoading || chartData.length === 0) && !error ? (
            <div className="h-64 flex items-center justify-center text-gray-500 bg-gray-800/50 rounded-xl border border-gray-700 animate-pulse">
              {isGathering ? "데이터 수집 시작 중..." : "실시간 스트림 연결 중..."}
            </div>
        ) : (
            <div className="space-y-6">
              <div className="p-5 bg-gray-800 border border-gray-700 rounded-xl shadow-lg">
                <div className="flex items-center justify-between mb-6">
                  <h3 className="text-lg font-semibold text-gray-100">실시간 채팅 화력</h3>
                  <span className="flex items-center text-[#00FFA3] text-xs font-bold animate-pulse">
                <span className="w-2 h-2 bg-[#00FFA3] rounded-full mr-2"></span> LIVE
              </span>
                </div>

                <div className="h-[400px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={displayData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <defs>
                        <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0.3}/>
                          <stop offset="95%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0}/>
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} opacity={0.3} />
                      <XAxis
                          dataKey="timestamp"
                          tickFormatter={formatXAxis}
                          stroke="#666"
                          fontSize={11}
                          tickMargin={12}
                          minTickGap={30}
                      />
                      <YAxis stroke="#666" fontSize={11} tickMargin={10} />
                      <Tooltip
                          labelFormatter={(value) => `시간: ${formatXAxis(value)}`}
                          contentStyle={{ backgroundColor: '#1a1a1c', border: '1px solid #333', borderRadius: '8px', color: '#fff' }}
                          itemStyle={{ color: CONFIG.CHART_COLOR }}
                      />
                      <Area
                          type="monotone"
                          dataKey="value"
                          stroke={CONFIG.CHART_COLOR}
                          strokeWidth={3}
                          fill="url(#colorValue)"
                          isAnimationActive={true}
                          /* 💡 하드코딩 하지 않고 계산된 dynamicDuration 사용 */
                          animationDuration={dynamicDuration}
                          animationEasing="linear"
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
