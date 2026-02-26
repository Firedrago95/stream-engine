import { useState, useEffect, useCallback } from 'react';
import {type StreamItem, StreamItemSchema } from '../types/stream';
import { z } from 'zod';

export const useStreams = (interval = 15000) => {
  const [streams, setStreams] = useState<StreamItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      const res = await fetch('/api/v1/streams', { signal });
      if (!res.ok) throw new Error('데이터를 가져오지 못했습니다.');
      const data = await res.json();

      const parsedData = z.array(StreamItemSchema).parse(data);

      setStreams(parsedData);
      setError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return; // 언마운트로 인한 취소는 에러로 취급하지 않음
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, []);

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
