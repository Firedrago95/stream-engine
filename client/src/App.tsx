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
  if (error) return <div className="text-red-500 p-8">에러 발생: {error}</div>;

  return (
      /* 💡 그리드 반응형 강화: 모바일 1열 -> 태블릿 2열 -> 노트북 3열 -> 대형 4열 */
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-6">
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
        {/* 좌측 사이드바 */}
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        {/* 우측 메인 영역 */}
        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main className="grow">
            <div className="px-8 lg:px-10 py-8 w-full max-w-9xl mx-auto">
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
