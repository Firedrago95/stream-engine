// ... 상단 import 문에서 ThemeToggle 제거
import UserMenu from '../components/DropdownProfile.jsx';
// import ThemeToggle from '../components/ThemeToggle.jsx'; // 💡 제거

function Header({ sidebarOpen, setSidebarOpen, variant = 'default' }) {
  return (
      <header className="...">
        <div className="px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16 ...">
            {/* Header: Left side */}
            <div className="flex">{/* 햄버거 버튼 생략 */}</div>

            {/* Header: Right side */}
            <div className="flex items-center space-x-3">
              {/* 💡 ThemeToggle 컴포넌트가 있던 자리를 삭제했습니다. */}

              {/* Divider */}
              <hr className="w-px h-6 bg-gray-200 dark:bg-gray-700/60 border-none" />

              {/* User Menu */}
              <UserMenu align="right" />
            </div>
          </div>
        </div>
      </header>
  );
}
