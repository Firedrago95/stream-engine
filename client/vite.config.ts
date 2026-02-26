import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  // 현재 작업 디렉토리의 환경 변수를 불러옵니다.
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    server: {
      port: Number(env.CLIENT_EXTERNAL_PORT) || 3000,
      proxy: {
        '/api': {
          // .env에 정의된 VITE_API_BASE_URL을 사용합니다.
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
        }
      }
    }
  }
})
