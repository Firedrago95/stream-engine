import React, { useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useStreamAnalysis } from '../../hooks/useStreamAnalysis';

/**
 * 대시보드 환경 설정
 */
const CONFIG = {
  POLLING_INTERVAL: 5000,      // 데이터 수집 주기 (5초)
  DISPLAY_POINTS: 20,          // X축 슬롯 개수 (20개 고정)
  CHART_COLOR: "#00FFA3",      // 치지직 연두색
  BG_DARK: "#1a1a1c",          // 카드 배경색
};

export const StreamAnalysisDashboard: React.FC = () => {
  const { streamId } = useParams<{ streamId: string }>();
  const navigate = useNavigate();

  const { analysisData, isLoading, error, isGathering } = useStreamAnalysis(
      streamId || '',
      CONFIG.POLLING_INTERVAL
  );

  const rawPoints = analysisData?.dataPoints || [];

  // 💡 [핵심] 실제 데이터가 시작되는 시점을 기억하여 그 이전 시간은 숨깁니다.
  const firstRealTimestamp = useMemo(() => rawPoints[0]?.timestamp, [rawPoints]);

  /**
   * 실제 데이터가 20개 미만일 때도 가상의 타임스탬프를 가진 빈 슬롯을 채워넣어 X축을 고정합니다.
   */
  const streamingData = useMemo(() => {
    const lastPoints = rawPoints.slice(-CONFIG.DISPLAY_POINTS);
    const paddingCount = CONFIG.DISPLAY_POINTS - lastPoints.length;

    if (paddingCount <= 0) return lastPoints;

    // 데이터가 부족한 앞부분을 빈 데이터로 채움 (타임스탬프 역산하여 축 고정)
    const referenceTime = rawPoints[0]?.timestamp || Date.now();
    const padding = Array.from({ length: paddingCount }, (_, i) => ({
      timestamp: referenceTime - (paddingCount - i) * CONFIG.POLLING_INTERVAL,
      value: null, // 선이 그려지지 않음
    }));

    return [...padding, ...lastPoints];
  }, [rawPoints]);

  /**
   * null이 뜨지 않게 하고, 데이터가 없는 구간은 빈칸으로 둡니다.
   */
  const formatXAxis = (ts: number) => {
    // 실제 데이터가 들어오기 전의 가상 슬롯이거나 ts가 없으면 빈칸 처리
    if (!ts || !firstRealTimestamp || ts < firstRealTimestamp) return "";

    return new Date(ts).toLocaleTimeString('ko-KR', {
      hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    });
  };

  if (!streamId) return <div className="p-10 text-center text-gray-400">잘못된 접근입니다.</div>;

  return (
      <div className="w-full max-w-7xl mx-auto">
        {/* 상단 헤더 영역 */}
        <div className="mb-6 flex items-center gap-3">
          <button onClick={() => navigate(-1)} className="p-2 bg-gray-800 border border-gray-700 rounded-lg hover:bg-gray-700 text-gray-400 transition-colors">
            <svg className="w-4 h-4 fill-current" viewBox="0 0 16 16"><path d="M6.7 14.7l1.4-1.4L3.8 9H16V7H3.8l4.3-4.3-1.4-1.4L0 8z" /></svg>
          </button>
          <h2 className="text-2xl font-bold text-gray-100">실시간 화력 분석 <span className="text-sm font-normal text-gray-500 ml-2">ID: {streamId}</span></h2>
        </div>

        {(isLoading || rawPoints.length === 0) && !error ? (
            <div className="h-64 flex items-center justify-center text-gray-500 bg-gray-800/50 rounded-xl border border-gray-700 animate-pulse">
              {isGathering ? "데이터 수집 시작 중..." : "데이터 연결 중..."}
            </div>
        ) : (
            <div className="p-5 bg-gray-800 border border-gray-700 rounded-xl shadow-lg">
              <div className="flex items-center justify-between mb-8">
                <h3 className="text-lg font-semibold text-gray-100">채널 화력 트렌드 (최근 20개 고정)</h3>
                <span className="flex items-center text-[#00FFA3] text-xs font-bold animate-pulse">
              <span className="w-2 h-2 bg-[#00FFA3] rounded-full mr-2"></span> LIVE
            </span>
              </div>

              <div className="h-[400px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={streamingData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0.3}/>
                        <stop offset="95%" stopColor={CONFIG.CHART_COLOR} stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#333" vertical={false} opacity={0.2} />

                    <XAxis
                        dataKey="timestamp"
                        tickFormatter={formatXAxis}
                        stroke="#444"
                        fontSize={11}
                        tickMargin={15}
                        interval={1}
                    />

                    <YAxis
                        stroke="#444"
                        fontSize={11}
                        tickMargin={10}
                        domain={[0, 'auto']}
                    />

                    <Tooltip
                        labelFormatter={(ts) => formatXAxis(ts as number) ? `시간: ${formatXAxis(ts as number)}` : ''}
                        contentStyle={{ backgroundColor: CONFIG.BG_DARK, border: '1px solid #333', borderRadius: '8px' }}
                        itemStyle={{ color: CONFIG.CHART_COLOR }}
                    />

                    <Area
                        type="monotone"
                        dataKey="value"
                        stroke={CONFIG.CHART_COLOR}
                        strokeWidth={3}
                        fill="url(#colorValue)"
                        isAnimationActive={true}
                        animationDuration={CONFIG.POLLING_INTERVAL}
                        animationEasing="linear"
                        connectNulls={false}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
        )}
      </div>
  );
};
