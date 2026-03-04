import { useState, useEffect, useCallback } from 'react';

export interface HighlightResponse {
  id: number;
  streamId: string;
  status: string; // 'ONGOING' | 'FINISHED'
  startTime: string;
  endTime: string | null;
  peakFirepower: number;
  durationSeconds: number;
}

export const useHighlights = (streamId: string, dateStr?: string, interval = 5000) => {
  const [highlights, setHighlights] = useState<HighlightResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    if (!streamId) return;
    try {
      // dateStr가 'realtime'이면 오늘 날짜, 아니면 선택된 날짜로 요청
      const queryDate = (!dateStr || dateStr === 'realtime') ? '' : `?date=${dateStr}`;
      const url = `/api/v1/analysis/streams/${streamId}/highlights${queryDate}`;

      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error('하이라이트 데이터를 가져오지 못했습니다.');

      const data = await res.json();
      setHighlights(data);
    } catch (err: any) {
      if (err.name !== 'AbortError') console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, [streamId, dateStr]);

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

  return { highlights, isLoading };
};
