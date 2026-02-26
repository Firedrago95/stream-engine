import { useState, useEffect, useCallback } from 'react';

export interface AnalysisDataPoint {
  timestamp: string;
  chatCount: number;
}

export const useStreamAnalysis = (streamId: string | undefined, interval = 5000) => {
  const [data, setData] = useState<AnalysisDataPoint[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAnalysis = useCallback(async (signal?: AbortSignal) => {
    if (!streamId) return;
    try {
      // 실제 API 엔드포인트에 맞게 수정 필요 (예: /api/v1/analysis/{streamId})
      const res = await fetch(`/api/v1/analysis/${streamId}`, { signal });
      if (!res.ok) throw new Error('분석 데이터를 가져오지 못했습니다.');

      const json = await res.json();
      setData(json);
      setError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, [streamId]);

  useEffect(() => {
    const controller = new AbortController();
    fetchAnalysis(controller.signal);

    const timer = setInterval(() => {
      fetchAnalysis(controller.signal);
    }, interval);

    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [fetchAnalysis, interval]);

  return { data, isLoading, error };
};
