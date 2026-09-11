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

export const useHighlights = (streamId: string, sessionId?: string, interval = 5000) => {
  const [highlights, setHighlights] = useState<HighlightResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    if (!streamId) return;
    try {
      const queryParam = (!sessionId || sessionId === 'realtime') ? '' : `?sessionId=${sessionId}`;
      const url = `${API_BASE_URL}/api/v1/analysis/streams/${streamId}/highlights${queryParam}`;

      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error('하이라이트 데이터를 가져오지 못했습니다.');

      const data = await res.json();
      setHighlights(data);
    } catch (err: any) {
      if (err.name !== 'AbortError') console.error(err);
    } finally {
      setIsLoading(false);
    }
  }, [streamId, sessionId]);

  const isRealtime = !sessionId || sessionId === 'realtime';

  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const controller = new AbortController();

    if (!isRealtime) {
      fetchData(controller.signal);
      return () => {
        controller.abort();
      };
    }

    const startPolling = () => {
      if (timer) clearInterval(timer);
      timer = setInterval(() => {
        fetchData(controller.signal);
      }, interval);
    };

    const handleVisibilityChange = () => {
      if (document.hidden) {
        if (timer) {
          clearInterval(timer);
          timer = null;
        }
      } else {
        fetchData(controller.signal);
        startPolling();
      }
    };

    fetchData(controller.signal);
    if (!document.hidden) {
      startPolling();
    }

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      if (timer) clearInterval(timer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      controller.abort();
    };
  }, [fetchData, interval, isRealtime]);

  return { highlights, isLoading };
};
