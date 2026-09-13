// @ts-nocheck
import React, { useState, useEffect, useRef } from "react";
import { NavLink, useLocation } from "react-router-dom";

function Sidebar({ sidebarOpen, setSidebarOpen, variant = 'default' }) {
  const location = useLocation();
  const { pathname } = location;
  const sidebar = useRef(null);
  const [sidebarExpanded, setSidebarExpanded] = useState(localStorage.getItem("sidebar-expanded") === "true");

  useEffect(() => {
    localStorage.setItem("sidebar-expanded", sidebarExpanded);
    if (sidebarExpanded) {
      document.querySelector("body").classList.add("sidebar-expanded");
    } else {
      document.querySelector("body").classList.remove("sidebar-expanded");
    }
  }, [sidebarExpanded]);

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
            ref={sidebar}
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
              <span className="text-[9px] font-extrabold text-gray-400 tracking-[0.25em] uppercase font-mono mt-1.5 opacity-70 group-hover:opacity-100 transition-opacity">
                STREAM ENGINE
              </span>
            </NavLink>

            {/* 모바일 전용 닫기 버튼 */}
            <button
              onClick={() => setSidebarOpen(false)}
              className="lg:hidden text-gray-400 hover:text-white p-1.5 rounded-lg hover:bg-gray-800 transition-colors"
              aria-label="사이드바 닫기"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 20 20">
                <path d="M10 8.586L2.929 1.515 1.515 2.929 8.586 10l-7.071 7.071 1.414 1.414L10 11.414l7.071 7.071 1.414-1.414L11.414 10l7.071-7.071-1.414-1.414L10 8.586z" />
              </svg>
            </button>
          </div>

          <div className="space-y-8">
            <div>
              <h3 className="text-xs uppercase text-[#a1a1aa] font-semibold pl-3">
                메뉴
              </h3>
              <ul className="mt-3">
                <li className={`px-3 py-2 rounded-lg mb-0.5 last:mb-0 ${pathname === '/' && 'bg-gray-800'}`}>
                  <NavLink
                    end
                    to="/"
                    onClick={() => setSidebarOpen(false)}
                    className={`block text-gray-100 truncate transition duration-150 ${pathname === '/' ? '' : 'hover:text-white'}`}
                  >
                    <div className="flex items-center">
                      <svg className={`shrink-0 h-6 w-6 fill-current ${pathname === '/' ? 'text-[#00FFA3]' : 'text-gray-500'}`} viewBox="0 0 24 24"><path d="M12 0C5.383 0 0 5.383 0 12s5.383 12 12 12 12-5.383 12-12S18.617 0 12 0z" /></svg>
                      <span className="text-sm font-bold ml-3 duration-200 whitespace-nowrap">라이브 대시보드</span>
                    </div>
                  </NavLink>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
  );
}
export default Sidebar;
