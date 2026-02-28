// @ts-nocheck
import React, { useState, useEffect, useRef } from "react";
import { NavLink, useLocation } from "react-router-dom";

function Sidebar({ sidebarOpen, setSidebarOpen, variant = 'default' }) {
  const location = useLocation();
  const { pathname } = location;
  const trigger = useRef(null);
  const sidebar = useRef(null);

  const storedSidebarExpanded = localStorage.getItem("sidebar-expanded");
  const [sidebarExpanded, setSidebarExpanded] = useState(storedSidebarExpanded === null ? false : storedSidebarExpanded === "true");

  useEffect(() => {
    const clickHandler = ({ target }) => {
      if (!sidebar.current || !trigger.current) return;
      if (!sidebarOpen || sidebar.current.contains(target) || trigger.current.contains(target)) return;
      setSidebarOpen(false);
    };
    document.addEventListener("click", clickHandler);
    return () => document.removeEventListener("click", clickHandler);
  });

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
        {/* 모바일용 배경 */}
        <div className={`fixed inset-0 bg-gray-900/30 z-40 md:hidden transition-opacity duration-200 ${sidebarOpen ? "opacity-100" : "opacity-0 pointer-events-none"}`} aria-hidden="true"></div>

        {/* 사이드바 본체 */}
        <div
            id="sidebar"
            ref={sidebar}
            className={`flex md:flex! flex-col absolute z-40 left-0 top-0 md:static md:left-auto md:top-auto md:translate-x-0 h-[100dvh] overflow-y-scroll md:overflow-y-auto no-scrollbar w-64 md:w-20 md:sidebar-expanded:!w-64 2xl:w-64! shrink-0 bg-white dark:bg-gray-800 p-4 transition-all duration-200 ease-in-out ${sidebarOpen ? "translate-x-0" : "-translate-x-64"} ${variant === 'v2' ? 'border-r border-gray-200 dark:border-gray-700/60' : 'rounded-r-2xl shadow-xs'}`}
        >
          <div className="flex justify-between mb-10 pr-3 sm:px-2">
            <button ref={trigger} className="md:hidden text-gray-500 hover:text-gray-400" onClick={() => setSidebarOpen(!sidebarOpen)}>
              <span className="sr-only">Close sidebar</span>
              <svg className="w-6 h-6 fill-current" viewBox="0 0 24 24"><path d="M10.7 18.7l1.4-1.4L7.8 13H20v-2H7.8l4.3-4.3-1.4-1.4L4 12z" /></svg>
            </button>
            <NavLink end to="/" className="block">
              <svg className="fill-[#00FFA3]" xmlns="http://www.w3.org/2000/svg" width={32} height={32}><path d="M31.956 14.8C31.372 6.92 25.08.628 17.2.044V5.76a9.04 9.04 0 0 0 9.04 9.04h5.716ZM14.8 26.24v5.716C6.92 31.372.63 25.08.044 17.2H5.76a9.04 9.04 0 0 1 9.04 9.04Zm11.44-9.04h5.716c-.584 7.88-6.876 14.172-14.756 14.756V26.24a9.04 9.04 0 0 1 9.04-9.04ZM.044 14.8C.63 6.92 6.92.628 14.8.044V5.76a9.04 9.04 0 0 1-9.04 9.04H.044Z" /></svg>
            </NavLink>
          </div>

          <div className="space-y-8">
            <div>
              <h3 className="text-xs uppercase text-gray-400 dark:text-gray-500 font-semibold pl-3">
                <span className="hidden md:block md:sidebar-expanded:hidden 2xl:hidden text-center w-6" aria-hidden="true">•••</span>
                <span className="md:hidden md:sidebar-expanded:block 2xl:block">Menu</span>
              </h3>
              <ul className="mt-3">
                <li className={`px-3 py-2 rounded-lg mb-0.5 last:mb-0 ${pathname === '/' && 'bg-gray-100 dark:bg-gray-900'}`}>
                  <NavLink end to="/" className={`block text-gray-800 dark:text-gray-100 truncate transition duration-150 ${pathname === '/' ? '' : 'hover:text-gray-900 dark:hover:text-white'}`}>
                    <div className="flex items-center">
                      <svg className={`shrink-0 h-6 w-6 fill-current ${pathname === '/' ? 'text-[#00FFA3]' : 'text-gray-400 dark:text-gray-500'}`} viewBox="0 0 24 24"><path d="M12 0C5.383 0 0 5.383 0 12s5.383 12 12 12 12-5.383 12-12S18.617 0 12 0z" /></svg>
                      <span className="text-sm font-medium ml-3 md:opacity-0 md:sidebar-expanded:opacity-100 2xl:opacity-100 duration-200">라이브</span>
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
