import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import ThemeProvider from './utils/ThemeContext'
import './index.css'
import './css/style.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <BrowserRouter>
        {/* 여기서 ThemeProvider를 사용해야 에러가 사라지고 다크모드가 적용 */}
        <ThemeProvider>
          <App />
        </ThemeProvider>
      </BrowserRouter>
    </StrictMode>,
)
