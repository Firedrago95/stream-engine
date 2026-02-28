import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import './css/style.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
    <StrictMode>
      {/* 💡 2. App 컴포넌트를 BrowserRouter로 감싸기 */}
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>,
)
