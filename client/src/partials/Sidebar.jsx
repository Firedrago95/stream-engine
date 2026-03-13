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
          <div className="flex items-center justify-start mb-10 pl-1.5 pr-3 sm:px-2">
            <NavLink end to="/" className="flex items-center gap-3 overflow-hidden">

              {/* 로고 */}
              <img
                  src="/cheese-pick-logo.png"
                  alt="Cheese-Pick Logo"
                  className="w-8 h-16 shrink-0 rounded"
              />

              {/* 💡 브랜드 텍스트: 숨김 클래스(opacity-0 등)를 전부 제거하여 무조건 보이게 수정 */}
              <span className="text-white font-extrabold text-xl tracking-tighter whitespace-nowrap mt-1">
                Cheese<span className="text-[#00FFA3]">Pick</span>
              </span>

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
