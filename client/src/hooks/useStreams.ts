import { useState, useEffect, useCallback } from 'react';
import { type StreamItem, StreamItemSchema } from '../types/stream';
import { z } from 'zod';

export const useStreams = (keyword = '', interval = 15000) => {
  const [streams, setStreams] = useState<StreamItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      const baseUrl = import.meta.env.VITE_API_URL || '';
      const url = keyword
          ? `${baseUrl}/api/v1/streams?keyword=${encodeURIComponent(keyword)}`
          : `${baseUrl}/api/v1/streams`;

      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error('데이터를 가져오지 못했습니다.');
      const data = await res.json();

      const parsedData = z.array(StreamItemSchema).parse(data);

      const sorted = [...parsedData].sort((a, b) =>
          (a.streamerName || '').localeCompare(b.streamerName || '')
      );

      setStreams(sorted);
      setError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, [keyword]); // 💡 keyword가 바뀔 때마다 fetchData가 갱신됩니다.

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

  return { streams, isLoading, error, refetch: fetchData };
};
