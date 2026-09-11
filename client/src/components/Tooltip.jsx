import React, { useState } from 'react';

function Tooltip({
  children,
  className = '',
  bg = 'dark',
  size = 'md',
  position = 'right',
}) {
  const [tooltipOpen, setTooltipOpen] = useState(false);

  const positionOuterClasses = (pos) => {
    switch (pos) {
      case 'right':
        return 'left-full top-1/2 -translate-y-1/2 ml-2';
      case 'left':
        return 'right-full top-1/2 -translate-y-1/2 mr-2';
      case 'bottom':
        return 'top-full left-1/2 -translate-x-1/2 mt-2';
      default:
        return 'bottom-full left-1/2 -translate-x-1/2 mb-2';
    }
  };

  const sizeClasses = (s) => {
    switch (s) {
      case 'lg':
        return 'min-w-72 px-3 py-2';
      case 'md':
        return 'min-w-56 px-3 py-2';
      case 'sm':
        return 'min-w-44 px-3 py-2';
      default:
        return 'px-3 py-2';
    }
  };

  const colorClasses = (b) => {
    switch (b) {
      case 'light':
        return 'bg-white text-gray-600 border-gray-200 shadow-xl';
      case 'dark':
        return 'bg-gray-800 text-gray-100 border-gray-700/80 shadow-2xl';
      default:
        return 'bg-gray-800 text-gray-100 border-gray-700/80 shadow-2xl';
    }
  };

  return (
    <div
      className={`relative inline-flex items-center ${className}`}
      onMouseEnter={() => setTooltipOpen(true)}
      onMouseLeave={() => setTooltipOpen(false)}
    >
      <div
        className="cursor-pointer flex items-center justify-center p-0.5"
        aria-label="안내 툴팁"
      >
        <svg className="fill-current text-gray-400 hover:text-gray-200 transition-colors" width="16" height="16" viewBox="0 0 16 16">
          <path d="M8 0C3.6 0 0 3.6 0 8s3.6 8 8 8 8-3.6 8-8-3.6-8-8-8zm0 12c-.6 0-1-.4-1-1s.4-1 1-1 1 .4 1 1-.4 1-1 1zm1-3H7V4h2v5z" />
        </svg>
      </div>

      {tooltipOpen && (
        <div
          className={`z-50 absolute pointer-events-none rounded-lg border text-left ${positionOuterClasses(position)} ${sizeClasses(size)} ${colorClasses(bg)} animate-in fade-in duration-150`}
          role="tooltip"
        >
          {children}
        </div>
      )}
    </div>
  );
}

export default Tooltip;
