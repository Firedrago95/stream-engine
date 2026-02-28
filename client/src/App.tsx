// @ts-nocheck
import React, { useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import Sidebar from './partials/Sidebar.jsx';
import Header from './partials/Header.jsx'; // 💡 .jsx 확장자를 명시하여 인식률을 높입니다.
import { StreamCard } from './components/stream/StreamCard';
import { StreamAnalysisDashboard } from './components/stream/StreamAnalysisDashboard';
import { useStreams } from './hooks/useStreams';
import './css/style.css';

// 💡 1. 메인 페이지 컴포넌트: 여기서 useStreams를 호출해야 15초마다 폴링이 돕니다.
const MainPage = () => {
  const { streams, isLoading, error } = useStreams(15000);

  if (isLoading) return <div className="text-gray-100 p-8">데이터 로딩 중...</div>;
  if (error) return <div className="text-red-500 p-8">에러 발생: {error}</div>;

  return (
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {streams.map((stream) => (
            <StreamCard key={stream.streamId} stream={stream} />
        ))}
      </div>
  );
};

// 💡 2. 전체 레이아웃 및 라우팅 설정
export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
      <div className="flex h-screen overflow-hidden bg-gray-900 font-inter text-gray-100">
        {/* 좌측 사이드바 */}
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        {/* 우측 메인 영역 */}
        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main>
            <div className="px-4 sm:px-6 lg:px-8 py-8 w-full max-w-9xl mx-auto">
              {/* 💡 Routes를 통해 URL에 맞는 컴포넌트를 이 자리에 렌더링합니다. */}
              <Routes>
                <Route path="/" element={<MainPage />} />
                <Route path="/streams/:streamId" element={<StreamAnalysisDashboard />} />
              </Routes>
            </div>
          </main>
        </div>
      </div>
  );
}
