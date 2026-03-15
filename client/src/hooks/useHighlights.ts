import { useState, useEffect, useCallback } from 'react';

// 💡 환경 변수 설정 (Cloudflare Pages에서 설정한 값)
const API_BASE_URL = import.meta.env.VITE_API_URL || '';

export interface HighlightResponse {
  id: number;
  streamId: string;
  status: string; // 'ONGOING' | 'FINISHED'
  startTime: string;
  endTime: string | null;
  peakFirepower: number;
  durationSeconds: number;
  startTimeOffset: number;
  endTimeOffset: number | null;
  externalVodId?: string | null;
}

export const useHighlights = (streamId: string, dateStr?: string, interval = 5000) => {
  const [highlights, setHighlights] = useState<HighlightResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    if (!streamId) return;
    try {
      const queryDate = (!dateStr || dateStr === 'realtime') ? '' : `?date=${dateStr}`;

      // 💡 주소 앞에 API_BASE_URL을 추가합니다.
      const url = `${API_BASE_URL}/api/v1/analysis/streams/${streamId}/highlights${queryDate}`;

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
