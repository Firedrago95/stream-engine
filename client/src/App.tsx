// @ts-nocheck
import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
// 💡 Mosaic 템플릿에서 가져온 컴포넌트들을 import 합니다.
import Sidebar from './partials/Sidebar.jsx'
import Header from './partials/Header';
import './css/style.css';

export const AppLayout: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
      // 💡 템플릿 기본 다크모드 배경색(slate-900) 적용
      <div className="flex h-screen overflow-hidden bg-slate-50 dark:bg-slate-900 font-inter">
        {/* Sidebar 컴포넌트 */}
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        {/* Content area */}
        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          {/* Header 컴포넌트 */}
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main>
            <div className="px-4 sm:px-6 lg:px-8 py-8 w-full max-w-9xl mx-auto">
              {/* 💡 여기에 MainPage 또는 Dashboard가 렌더링됩니다. */}
              <Outlet />
            </div>
          </main>
        </div>
      </div>
  );
};

export default AppLayout;
