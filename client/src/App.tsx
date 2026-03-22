// @ts-nocheck
import React, { useState, useEffect, useRef } from 'react';
import { Routes, Route } from 'react-router-dom';
import Sidebar from './partials/Sidebar.jsx';
import Header from './partials/Header.jsx';
import { StreamCard } from './components/stream/StreamCard';
import { StreamAnalysisDashboard } from './components/stream/StreamAnalysisDashboard';
import { useStreams } from './hooks/useStreams';
import Tooltip from './components/Tooltip.jsx'; // 🔥 툴팁 컴포넌트 추가
import './css/style.css';

const MainPage = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const { streams, isLoading, error } = useStreams(searchTerm, 15000);

  // 100개 대응: 브라우저 부하 방지를 위한 클라이언트 사이드 무한 스크롤
  const [visibleCount, setVisibleCount] = useState(24);
  const loadMoreRef = useRef(null);

  // 검색어가 변경되면 보이는 개수 초기화
  useEffect(() => {
    setVisibleCount(24);
  }, [searchTerm]);

  // 스크롤이 하단에 닿으면 24개씩 추가 렌더링
  useEffect(() => {
    const observer = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting) {
            setVisibleCount((prev) => prev + 24);
          }
        },
        { threshold: 0.1 }
    );

    if (loadMoreRef.current) {
      observer.observe(loadMoreRef.current);
    }

    return () => observer.disconnect();
  }, [streams.length]);

  if (error) return <div className="text-rose-500 p-8 text-center">에러: {error}</div>;

  // 화면에 렌더링할 데이터만 잘라냄
  const displayedStreams = streams.slice(0, visibleCount);

  return (
      <div className="w-full">

        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-8 px-1 mt-4">
          <div className="flex items-center gap-2 mb-2 sm:mb-0">
            <h2 className="text-2xl font-bold text-gray-100 italic uppercase tracking-tighter whitespace-nowrap">
              실시간 라이브
            </h2>
            <Tooltip bg="dark" position="right" size="sm" className="mt-0.5">
              <div className="text-xs text-gray-200 font-medium whitespace-nowrap">
                연령 제한 방송은 분석에서 제외됩니다.
              </div>
            </Tooltip>
          </div>

          <div className="relative w-full sm:max-w-xs min-w-[140px]">
            <input
                type="text"
                placeholder="스트리머 검색 (오프라인 포함)"
                className="w-full p-2.5 bg-[#1a1a1c] border border-gray-800 rounded-lg text-sm text-white placeholder-white/70 focus:outline-none focus:border-[#00FFA3] hover:border-[#00FFA3] hover:ring-1 hover:ring-[#00FFA3] hover:shadow-lg transition-all duration-200"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
            />
            {searchTerm && (
                <button
                    onClick={() => setSearchTerm("")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#a1a1aa] hover:text-white text-xs font-bold"
                >
                  ✕
                </button>
            )}
          </div>
        </div>

        {/* 로딩 상태 표시 */}
        {isLoading && streams.length === 0 && (
            <div className="text-[#a1a1aa] p-20 text-center animate-pulse">방송 목록을 불러오는 중...</div>
        )}

        {/* 결과 없음 처리 */}
        {!isLoading && streams.length === 0 && (
            <div className="text-center py-20 text-[#a1a1aa] bg-[#1a1a1c] rounded-2xl border border-gray-800">
              {searchTerm ? `'${searchTerm}'에 대한 검색 결과가 없습니다.` : "현재 라이브 중인 방송이 없습니다."}
            </div>
        )}

        {/* 기존 스타일 유지, 렌더링 목록만 displayedStreams로 변경 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-6">
          {displayedStreams.map((stream) => (
              <StreamCard key={stream.streamId} stream={stream} />
          ))}
        </div>

        {/* 100개 대응: 무한 스크롤 감지용 투명 요소 */}
        {visibleCount < streams.length && (
            <div ref={loadMoreRef} className="w-full h-10 mt-8 flex justify-center items-center">
              <div className="w-6 h-6 border-2 border-[#00FFA3] border-t-transparent rounded-full animate-spin opacity-30"></div>
            </div>
        )}

        <footer className="mt-24 pt-12 pb-12 border-t border-gray-800/60 text-center w-full">
          <div className="mb-6">
            <span className="text-[#00FFA3] font-black text-xl italic tracking-tighter uppercase">Cheese Pick</span>
          </div>
          <a
              href="https://forms.gle/hUkZBr9KCTDyTXLW9"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 text-gray-300 hover:text-[#00FFA3] transition-all text-sm font-bold bg-[#1a1a1c] px-8 py-3.5 rounded-full border border-gray-700 hover:border-[#00FFA3]/50 shadow-xl group"
          >
            💡 치즈픽 하이라이트 엔진 피드백 보내기
            <svg className="w-4 h-4 transform group-hover:translate-x-1 transition-transform" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </a>
          <p className="mt-8 text-[11px] text-gray-500 font-medium tracking-widest uppercase">
            © 2026 CheesePick. Advanced Stream Analytics Pipeline.
          </p>
        </footer>
      </div>
  );
};

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
      <div className="flex h-screen overflow-hidden bg-gray-900 font-inter text-gray-100">
        <Sidebar sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

        <div className="relative flex flex-col flex-1 overflow-y-auto overflow-x-hidden">
          <Header sidebarOpen={sidebarOpen} setSidebarOpen={setSidebarOpen} />

          <main className="grow">
            <div className="w-[95%] lg:w-[80%] mx-auto py-8">
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
