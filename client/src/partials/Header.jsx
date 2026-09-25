import React from 'react';
import UserMenu from '../components/DropdownProfile.jsx';

// 💡 선언과 동시에 export default를 수행하여 'named export' 에러를 방지합니다.
export default function Header({ sidebarOpen, setSidebarOpen, variant = 'default' }) {
  return (
      <header className={`sticky top-0 before:absolute before:inset-0 before:backdrop-blur-md max-lg:before:bg-white/90 dark:max-lg:before:bg-gray-800/90 before:-z-10 z-30 ${variant === 'v2' || variant === 'v3' ? 'before:bg-white after:absolute after:h-px after:inset-x-0 after:top-full after:bg-gray-200 dark:after:bg-gray-700/60 after:-z-10' : 'max-lg:shadow-xs lg:before:bg-gray-100/90 dark:lg:before:bg-gray-900/90'} ${variant === 'v2' ? 'dark:before:bg-gray-800' : ''} ${variant === 'v3' ? 'dark:before:bg-gray-900' : ''}`}>
        <div className="px-4 sm:px-6 lg:px-8">
          <div className={`flex items-center justify-between h-16 ${variant === 'v2' || variant === 'v3' ? '' : 'lg:border-b border-gray-200 dark:border-gray-700/60'}`}>
            <div className="flex">
              {/* Hamburger button (모바일 및 태블릿에서 사이드바 토글) */}
              <button
                  className="text-gray-100 hover:text-white lg:hidden p-2 -ml-2 rounded-xl hover:bg-gray-800 active:bg-gray-700 transition-colors"
                  onClick={(e) => { e.stopPropagation(); setSidebarOpen(!sidebarOpen); }}
                  aria-label="사이드바 메뉴 열기"
              >
                <span className="sr-only">Open sidebar</span>
                <svg className="w-6 h-6 fill-current" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <rect x="4" y="5" width="16" height="2" rx="1" /><rect x="4" y="11" width="16" height="2" rx="1" /><rect x="4" y="17" width="16" height="2" rx="1" />
                </svg>
              </button>
            </div>


          </div>
        </div>
      </header>
  );
}
