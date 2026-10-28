// @ts-nocheck
import React from "react";
import { NavLink, useLocation } from "react-router-dom";

function Sidebar({ sidebarOpen, setSidebarOpen, variant = 'default' }) {
  const location = useLocation();
  const { pathname } = location;

  return (
      <div className="min-w-fit">
        {/* 모바일/태블릿 배경 오버레이 (클릭 시 사이드바 닫기) */}
        <div
            className={`fixed inset-0 bg-gray-950/70 backdrop-blur-xs z-40 lg:hidden transition-opacity duration-200 ${sidebarOpen ? "opacity-100" : "opacity-0 pointer-events-none"}`}
            aria-hidden="true"
            onClick={() => setSidebarOpen(false)}
        ></div>

        <div
            id="sidebar"
            className={`flex flex-col fixed lg:static z-40 left-0 top-0 lg:left-auto lg:top-auto h-[100dvh] overflow-y-auto no-scrollbar w-64 shrink-0 bg-[#141416] border-r border-gray-800 p-4 transition-transform duration-200 ease-in-out ${sidebarOpen ? "translate-x-0" : "-translate-x-64 lg:translate-x-0"} ${variant === 'v2' ? '' : 'shadow-xl'}`}
        >
          {/* 헤더/로고 영역: 순수 텍스트 단일 브랜드 마크 */}
          <div className="flex items-center justify-between mb-8 px-2">
            <NavLink
              end
              to="/"
              onClick={() => setSidebarOpen(false)}
              className="flex flex-col justify-center leading-none group py-1"
            >
              {/* 텍스트 중심 단일 브랜드 타이포그래피: CHEESE (네온 옐로) + PICK (네온 그린) */}
              <div className="flex items-center text-2xl font-black italic tracking-tighter uppercase select-none transition-transform duration-200 group-hover:scale-[1.02]">
                <span className="text-[#FACC15] drop-shadow-[0_0_12px_rgba(250,204,21,0.45)]">CHEESE</span>
                <span className="text-[#00FFA3] ml-1.5 drop-shadow-[0_0_12px_rgba(0,255,163,0.5)]">PICK</span>
              </div>
              <span className="text-[9px] font-extrabold text-gray-200 tracking-[0.25em] uppercase font-mono mt-1.5 opacity-70 group-hover:opacity-100 transition-opacity">
                STREAM ENGINE
              </span>
            </NavLink>

            {/* 모바일 전용 닫기 버튼 */}
            <button
              onClick={() => setSidebarOpen(false)}
              className="lg:hidden text-gray-100 hover:text-white p-1.5 rounded-lg hover:bg-gray-800 transition-colors"
              aria-label="사이드바 닫기"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 20 20">
                <path d="M10 8.586L2.929 1.515 1.515 2.929 8.586 10l-7.071 7.071 1.414 1.414L10 11.414l7.071 7.071 1.414-1.414L11.414 10l7.071-7.071-1.414-1.414L10 8.586z" />
              </svg>
            </button>
          </div>

          <div className="space-y-8">
            <div>
              <h3 className="text-xs uppercase tracking-wider text-gray-100 font-bold pl-3">
                메뉴
              </h3>
              <ul className="mt-3 space-y-1.5 flex flex-col">
                {/* 1. 실시간 라이브 */}
                <li className="order-1">
                  <NavLink
                    end
                    to="/"
                    onClick={() => setSidebarOpen(false)}
                    className={({ isActive }) =>
                      `group flex items-center px-3 py-2.5 rounded-xl transition-all duration-150 ${
                        (pathname === '/' || (pathname.startsWith('/streams') && !location.search.includes('sessionId=')))
                          ? 'bg-[#00FFA3]/15 text-[#00FFA3] font-black border border-[#00FFA3]/40 shadow-[0_0_15px_rgba(0,255,163,0.15)]'
                          : 'text-gray-100 hover:text-white hover:bg-gray-800/80 font-bold border border-transparent'
                      }`
                    }
                  >
                    <svg
                      className={`shrink-0 h-5 w-5 transition-colors ${
                        (pathname === '/' || (pathname.startsWith('/streams') && !location.search.includes('sessionId=')))
                          ? 'text-[#00FFA3] drop-shadow-[0_0_8px_rgba(0,255,163,0.6)]'
                          : 'text-gray-100 group-hover:text-white'
                      }`}
                      fill="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <circle cx="12" cy="12" r="10" />
                    </svg>
                    <span className="text-sm font-bold ml-3 duration-200 whitespace-nowrap">실시간 라이브</span>
                  </NavLink>
                </li>

                {/* 2. 방송 하이라이트 (NEW) */}
                <li className="order-2">
                  <NavLink
                    to="/highlights"
                    onClick={() => setSidebarOpen(false)}
                    className={({ isActive }) =>
                      `group flex items-center px-3 py-2.5 rounded-xl transition-all duration-150 ${
                        pathname.startsWith('/highlights') || (pathname.startsWith('/streams') && location.search.includes('sessionId='))
                          ? 'bg-[#00FFA3]/15 text-[#00FFA3] font-black border border-[#00FFA3]/40 shadow-[0_0_15px_rgba(0,255,163,0.15)]'
                          : 'text-gray-100 hover:text-white hover:bg-gray-800/80 font-bold border border-transparent'
                      }`
                    }
                  >
                    <svg
                      className={`shrink-0 h-5 w-5 transition-colors ${
                        pathname.startsWith('/highlights') || (pathname.startsWith('/streams') && location.search.includes('sessionId='))
                          ? 'text-[#00FFA3] drop-shadow-[0_0_8px_rgba(0,255,163,0.6)]'
                          : 'text-gray-100 group-hover:text-white'
                      }`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <span className="text-sm font-bold ml-3 duration-200 whitespace-nowrap">방송 하이라이트</span>
                    <span className="ml-auto px-1.5 py-0.5 text-[9px] font-black uppercase tracking-wider rounded bg-yellow-400/20 text-yellow-300 border border-yellow-400/40">
                      AI
                    </span>
                  </NavLink>
                </li>

                {/* 3. 스트리머 리포트 */}
                <li className="order-3">
                  <NavLink
                    to="/streamers"
                    onClick={() => setSidebarOpen(false)}
                    className={({ isActive }) =>
                      `group flex items-center px-3 py-2.5 rounded-xl transition-all duration-150 ${
                        pathname.startsWith('/streamers')
                          ? 'bg-[#00FFA3]/15 text-[#00FFA3] font-black border border-[#00FFA3]/40 shadow-[0_0_15px_rgba(0,255,163,0.15)]'
                          : 'text-gray-100 hover:text-white hover:bg-gray-800/80 font-bold border border-transparent'
                      }`
                    }
                  >
                    <svg
                      className={`shrink-0 h-5 w-5 transition-colors ${
                        pathname.startsWith('/streamers')
                          ? 'text-[#00FFA3] drop-shadow-[0_0_8px_rgba(0,255,163,0.6)]'
                          : 'text-gray-100 group-hover:text-white'
                      }`}
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                    </svg>
                    <span className="text-sm font-bold ml-3 duration-200 whitespace-nowrap">스트리머 리포트</span>
                  </NavLink>
                </li>
              </ul>
            </div>
          </div>

          {/* 하단 피드백 영역: 상시 노출 구글폼 피드백 배너 */}
          <div className="mt-auto pt-4 border-t border-gray-800/80">
            <a
              href="https://forms.gle/hUkZBr9KCTDyTXLW9"
              target="_blank"
              rel="noopener noreferrer"
              className="group block p-3 rounded-xl bg-[#1a1a1c]/80 hover:bg-[#1a1a1c] border border-gray-800 hover:border-[#00FFA3]/50 transition-all duration-200 shadow-sm"
            >
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-bold text-gray-200 group-hover:text-[#00FFA3] transition-colors flex items-center gap-1.5">
                  <span>💡</span> 피드백 보내기
                </span>
                <svg
                  className="w-3.5 h-3.5 text-gray-400 group-hover:text-[#00FFA3] group-hover:translate-x-0.5 transition-all"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </div>
              <p className="text-[11px] text-gray-400 group-hover:text-gray-300 leading-relaxed transition-colors">
                서비스 의견이나 개선점을 들려주세요!
              </p>
            </a>
          </div>
        </div>
      </div>
  );
}
export default Sidebar;
