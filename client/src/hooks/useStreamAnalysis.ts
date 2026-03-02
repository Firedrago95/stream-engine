// client/src/hooks/useStreamAnalysis.ts
import { useState, useEffect, useCallback } from 'react';

export const useStreamAnalysis = (streamId: string, interval = 5000) => {
  const [analysisData, setAnalysisData] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 상태 추가: 404 에러일 경우 수집 중임을 나타내는 플래그
  const [isGathering, setIsGathering] = useState(false);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      const res = await fetch(`/api/v1/analysis/${streamId}`, { signal });

      // 💡 핵심 수정: 404 에러(데이터 없음) 처리
      if (res.status === 404) {
        setIsGathering(true); // 수집 중 상태로 변경
        setError(null);
        return; // 에러를 던지지 않고 조용히 빠져나감
      }

      if (!res.ok) throw new Error('분석 데이터를 가져오지 못했습니다.');

      const data = await res.json();
      setIsGathering(false); // 데이터가 도착하면 수집 중 상태 해제
      setAnalysisData(data); // 데이터 저장
      setError(null);

    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, [streamId]);

  // 💡 생략했던 useEffect 로직 원상복구!
  useEffect(() => {
    const controller = new AbortController();

    // 컴포넌트 마운트 시 즉시 1회 호출
    fetchData(controller.signal);

    // interval(5초)마다 주기적으로 호출
    const timer = setInterval(() => {
      fetchData(controller.signal);
    }, interval);

    // 컴포넌트 언마운트 시 타이머 및 fetch 취소 정리
    return () => {
      clearInterval(timer);
      controller.abort();
    };
  }, [fetchData, interval]);

  return { analysisData, isLoading, error, isGathering, refetch: fetchData };
};
