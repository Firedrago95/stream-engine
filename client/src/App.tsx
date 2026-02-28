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

  if (isLoading) return <div className="text-gray-400 p-8 text-center">데이터 로딩 중...</div>;
  if (error) return <div className="text-rose-500 p-8 text-center">에러: {error}</div>;

  return (
      /* 💡 여기가 핵심입니다. max-w-7xl mx-auto를 통해 분석 페이지와 똑같은 폭의 '슬롯'을 만듭니다. */
      <div className="w-full max-w-7xl mx-auto">
        <h2 className="text-2xl font-bold text-gray-100 mb-6 px-1">실시간 라이브</h2>
        {/* 방송 목록 그리드: 카드 내부의 글자/이미지는 왼쪽 정렬을 유지합니다. */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {streams.map((stream) => (
              <StreamCard key={stream.streamId} stream={stream} />
          ))}
        </div>
      </div>
  );
};

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
      <div className="flex h-screen overflow-hidden bg-gray-900 font-inter text-gray-100">
        {/* 1. 사이드바: 공간을 차지하도록 하여 콘텐츠가 그 밑으로 들어가는 것을 원천 봉쇄합니다. */}
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        {/* 2. 메인 영역 */}
        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main className="grow">
            {/* 모든 페이지가 이 px-4~8 패딩과 너비 규칙을 공유합니다. */}
            <div className="px-4 sm:px-6 lg:px-8 py-8 w-full">
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
