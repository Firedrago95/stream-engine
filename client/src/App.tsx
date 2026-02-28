// @ts-nocheck
import React, { useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import Sidebar from './partials/Sidebar.jsx';
import Header from './partials/Header.jsx';
import { StreamCard } from './components/stream/StreamCard';
import { StreamAnalysisDashboard } from './components/stream/StreamAnalysisDashboard';
import { useStreams } from './hooks/useStreams';
import './css/style.css';

const MainPage = () => {
  const { streams, isLoading, error } = useStreams(15000);

  if (isLoading) return <div className="text-gray-100 p-8">데이터 로딩 중...</div>;
  if (error) return <div className="text-red-500 p-8 text-center">연결 에러: {error}</div>;

  return (
      /* 💡 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 등 반응형으로 배치 */
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {streams.map((stream) => (
            <StreamCard key={stream.streamId} stream={stream} />
        ))}
      </div>
  );
};

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
      <div className="flex h-screen overflow-hidden bg-gray-900 font-inter text-gray-100">
        {/* 1. 사이드바 - 공간을 확실히 차지함 */}
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        {/* 2. 우측 메인 영역 - flex-1로 남은 모든 공간 차지 */}
        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main className="grow">
            {/* 💡 px-8 lg:px-12로 좌우 여백을 주어 카드들이 사이드바 뒤로 숨지 않게 합니다. */}
            <div className="px-8 lg:px-12 py-8 w-full max-w-9xl mx-auto">
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
