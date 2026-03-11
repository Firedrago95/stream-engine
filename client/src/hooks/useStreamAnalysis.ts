import { useState, useEffect, useCallback } from 'react';

// 1. API 베이스 URL 설정 (환경 변수에서 가져오고 없으면 빈 문자열)
const API_BASE_URL = import.meta.env.VITE_API_URL || '';

export const useStreamAnalysis = (streamId: string, interval = 5000) => {
  const [analysisData, setAnalysisData] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isGathering, setIsGathering] = useState(false);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      // 2. fetch 주소에 API_BASE_URL을 붙여줍니다.
      const res = await fetch(`${API_BASE_URL}/api/v1/analysis/${streamId}`, { signal });

      if (res.status === 404) {
        setIsGathering(true);
        setError(null);
        return;
      }

      if (!res.ok) throw new Error('분석 데이터를 가져오지 못했습니다.');

      const data = await res.json();
      setIsGathering(false);
      setAnalysisData(data);
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
    fetchData(controller.signal);

    const timer = setInterval(() => {
      fetchData(controller.signal);
    }, interval);

    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [fetchData, interval]);

  return { analysisData, isLoading, error, isGathering, refetch: fetchData };
};
