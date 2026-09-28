import { useState, useEffect } from 'react';
import type { StreamerSimilarityResponse } from '../types/StreamerSimilarity';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const useStreamerSimilarity = (channelId: string | undefined) => {
  const [data, setData] = useState<StreamerSimilarityResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(channelId));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!channelId) {
      return;
    }

    const controller = new AbortController();

    fetch(`${API_BASE_URL}/api/v1/streamers/${encodeURIComponent(channelId)}/similarities`, {
      signal: controller.signal,
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error('시청자 유사 채널 데이터를 불러오지 못했습니다.');
        }
        return res.json();
      })
      .then((json: StreamerSimilarityResponse) => {
        setData(json);
        setError(null);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === 'AbortError') {
          return;
        }
        setError(err instanceof Error ? err.message : '알 수 없는 오류');
        setIsLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, [channelId]);

  return { data, isLoading, error };
};
