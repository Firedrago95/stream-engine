import { useState, useEffect } from 'react';
import type { WeeklyCategory } from '../components/stream/WeeklyCategoryRanking';

const DEFAULT_CATEGORIES: WeeklyCategory[] = [
  { rank: 1, categoryName: '메이플스토리', accumulatedViewHours: '약 6.6만 시간', exactHours: 65838, change: 'up', changeValue: 2, icon: '🍁' },
  { rank: 2, categoryName: '마인크래프트', accumulatedViewHours: '약 5.8만 시간', exactHours: 57543, change: 'up', changeValue: 3, icon: '⛏️' },
  { rank: 3, categoryName: '종합 게임', accumulatedViewHours: '약 4.2만 시간', exactHours: 42421, change: 'same', icon: '🎮' },
  { rank: 4, categoryName: '리그 오브 레전드', accumulatedViewHours: '약 4.2만 시간', exactHours: 42171, change: 'down', changeValue: 3, icon: '⚔️' },
  { rank: 5, categoryName: '오버워치', accumulatedViewHours: '약 3.0만 시간', exactHours: 30402, change: 'new', icon: '🎯' },
  { rank: 6, categoryName: '이터널 리턴', accumulatedViewHours: '약 2.5만 시간', exactHours: 25044, change: 'down', changeValue: 1, icon: '🧪' },
];

export const useWeeklyCategories = () => {
  const [categories, setCategories] = useState<WeeklyCategory[]>(DEFAULT_CATEGORIES);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    const fetchCategories = async () => {
      try {
        const baseUrl = import.meta.env.VITE_API_URL || '';
        const res = await fetch(`${baseUrl}/api/v1/categories/weekly-ranking`, {
          signal: controller.signal,
        });
        if (!res.ok) throw new Error('카테고리 랭킹을 불러오지 못했습니다.');
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          setCategories(data);
        }
        setError(null);
      } catch (err: any) {
        if (err.name === 'AbortError') return;
        setError(err instanceof Error ? err.message : '네트워크 오류');
      } finally {
        setIsLoading(false);
      }
    };

    fetchCategories();

    return () => {
      controller.abort();
    };
  }, []);

  return { categories, isLoading, error };
};
