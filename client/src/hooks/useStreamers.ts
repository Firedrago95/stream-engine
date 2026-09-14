import { useState, useEffect, useCallback } from 'react';
import { type StreamItem, StreamItemSchema } from '../types/stream';
import { z } from 'zod';

export const useStreamers = (keyword = '', interval = 30000) => {
  const [streamers, setStreamers] = useState<StreamItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      const baseUrl = import.meta.env.VITE_API_URL || '';
      const url = keyword
        ? `${baseUrl}/api/v1/streamers?keyword=${encodeURIComponent(keyword)}`
        : `${baseUrl}/api/v1/streamers`;

      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error('스트리머 목록을 가져오지 못했습니다.');
      const data = await res.json();

      const parsedData = z.array(StreamItemSchema).parse(data);

      setStreamers(parsedData);
      setError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const controller = new AbortController();

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
  }, [fetchData, interval]);

  return { streamers, isLoading, error, refetch: fetchData };
};
