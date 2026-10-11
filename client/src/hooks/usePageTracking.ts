import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { trackPageView } from '../utils/analytics';

export const usePageTracking = () => {
  const location = useLocation();

  useEffect(() => {
    const fullPath = location.pathname + location.search + location.hash;
    trackPageView(fullPath);
  }, [location]);
};
