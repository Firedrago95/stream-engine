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
        {/* 모바일 배경 */}
        <div className={`fixed inset-0 bg-gray-900/30 z-40 md:hidden transition-opacity duration-200 ${sidebarOpen ? "opacity-100" : "opacity-0 pointer-events-none"}`} aria-hidden="true"></div>

        <div
            id="sidebar"
            ref={sidebar}
            className={`flex flex-col absolute z-40 left-0 top-0 md:static md:left-auto md:top-auto md:translate-x-0 h-[100dvh] overflow-y-scroll md:overflow-y-auto no-scrollbar w-64 md:w-20 lg:sidebar-expanded:!w-64 2xl:w-64! shrink-0 bg-[#1a1a1c] border-r border-gray-800 p-4 transition-all duration-200 ease-in-out ${sidebarOpen ? "translate-x-0" : "-translate-x-64"} ${variant === 'v2' ? '' : 'shadow-xl'}`}
        >
          {/* 헤더/로고 영역 */}
          <div className="flex items-center justify-start mb-8 pl-1.5 pr-2">
            <NavLink end to="/" className="flex items-center gap-3 overflow-hidden group py-1">
              {/* 치즈/스파크 네온 그린 & 골드 지오메트릭 심볼 */}
              <div className="relative w-8 h-8 flex-shrink-0 flex items-center justify-center">
                <svg
                  className="w-8 h-8 filter drop-shadow-[0_0_10px_rgba(0,255,163,0.45)] transition-transform duration-300 group-hover:scale-110"
                  viewBox="0 0 36 36"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                >
                  {/* 외곽 네온 그린 스파크/버스트 */}
                  <polygon
                    fill="#00FFA3"
                    points="18,2 22,12 33,8 26,18 35,26 23,26 21,36 15,27 4,32 10,21 2,14 13,14"
                  />
                  {/* 내부 네온 옐로 치즈 코어 */}
                  <polygon
                    fill="#FACC15"
                    points="18,6 20,13 28,11 23,18 29,24 20,24 18,31 14,24 6,27 10,19 4,15 13,15"
                  />
                  {/* 중앙 다크 보이드 홀 */}
                  <circle cx="17" cy="18" r="3.5" fill="#141416" />
                </svg>
              </div>

              {/* 텍스트 중심 브랜드 타이포그래피: CHEESE (네온 옐로) + PICK (네온 그린) */}
              <div className="flex flex-col justify-center leading-none">
                <div className="flex items-center text-[19px] font-black italic tracking-tighter uppercase select-none">
                  <span className="text-[#FACC15] drop-shadow-[0_0_8px_rgba(250,204,21,0.4)]">CHEESE</span>
                  <span className="text-[#00FFA3] ml-1.5 drop-shadow-[0_0_8px_rgba(0,255,163,0.4)]">PICK</span>
                </div>
                <span className="text-[9px] font-extrabold text-gray-400 tracking-[0.22em] uppercase font-mono mt-1 opacity-80">
                  STREAM ENGINE
                </span>
              </div>
            </NavLink>
          </div>

          <div className="space-y-8">
            <div>
              <h3 className="text-xs uppercase text-[#a1a1aa] font-semibold pl-3">
                <span className="hidden md:block lg:sidebar-expanded:hidden 2xl:hidden text-center w-6" aria-hidden="true">•••</span>
                <span className="md:hidden lg:sidebar-expanded:block 2xl:block">메뉴</span>
              </h3>
              <ul className="mt-3">
                <li className={`px-3 py-2 rounded-lg mb-0.5 last:mb-0 ${pathname === '/' && 'bg-gray-800'}`}>
                  <NavLink end to="/" className={`block text-gray-100 truncate transition duration-150 ${pathname === '/' ? '' : 'hover:text-white'}`}>
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
