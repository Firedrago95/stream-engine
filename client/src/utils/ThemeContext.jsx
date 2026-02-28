// src/utils/ThemeContext.jsx
import { createContext, useContext, useEffect } from 'react';

const ThemeContext = createContext({
  currentTheme: 'dark',
});

export default function ThemeProvider({ children }) {
  // 💡 사용자의 선택권 없이 무조건 'dark'로 고정합니다.
  useEffect(() => {
    document.documentElement.classList.add('dark');
    document.documentElement.style.colorScheme = 'dark';

    // Mosaic 템플릿의 다크모드 배경색(slate-900)이 즉시 적용되도록 합니다.
  }, []);

  return (
      <ThemeContext.Provider value={{ currentTheme: 'dark' }}>
        {children}
      </ThemeContext.Provider>
  );
}

export const useThemeProvider = () => useContext(ThemeContext);
