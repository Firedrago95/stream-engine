import { useState, useEffect, useCallback } from 'react';
import type {StreamItem} from '../types/stream';

export const useStreams = (interval = 15000) => {
  const [streams, setStreams] = useState<StreamItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const res = await fetch('/api/v1/streams');
      if (!res.ok) throw new Error('데이터를 가져오지 못했습니다.');
      const data = await res.json();
      setStreams(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
    const timer = setInterval(fetchData, interval);
    return () => clearInterval(timer);
  }, [fetchData, interval]);

  return { streams, isLoading, error, refetch: fetchData };
};
