import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // 로컬 프론트에서 '/api'로 시작하는 요청을 가로챕니다.
      '/api': {
        target: 'https://api.cheesepick.me', // 실제 백엔드 API 서버 주소
        changeOrigin: true,
        secure: false,
      }
    }
  }
});
