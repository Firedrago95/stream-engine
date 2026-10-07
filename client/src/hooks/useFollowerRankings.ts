import { useState, useEffect, useCallback } from 'react';
import { type FollowerRankingItem, type FollowerRankingType, FollowerRankingItemSchema } from '../types/followerRanking';
import { z } from 'zod';

export const useFollowerRankings = (type: FollowerRankingType = 'GROWTH', limit = 100, interval = 60000) => {
  const [rankings, setRankings] = useState<FollowerRankingItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (signal?: AbortSignal) => {
    try {
      const baseUrl = import.meta.env.VITE_API_URL || '';
      const url = `${baseUrl}/api/v1/streamers/leaderboard/followers?type=${type}&limit=${limit}`;

      const res = await fetch(url, { signal });
      if (!res.ok) throw new Error('팔로워 랭킹 목록을 가져오지 못했습니다.');
      const data = await res.json();

      const parsedData = z.array(FollowerRankingItemSchema).parse(data);
      setRankings(parsedData);
      setError(null);
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setError(err instanceof Error ? err.message : '네트워크 오류');
    } finally {
      setIsLoading(false);
    }
  }, [type, limit]);

  useEffect(() => {
    setIsLoading(true);
    const controller = new AbortController();
    fetchData(controller.signal);

    const timer = setInterval(() => {
      fetchData(controller.signal);
    }, interval);

    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [fetchData, interval]);

  return { rankings, isLoading, error, refetch: fetchData };
};
