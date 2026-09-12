import React from 'react';

export const StreamCardSkeleton: React.FC = () => {
  return (
    <div className="flex items-center p-3 sm:p-4 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-500 rounded-xl animate-pulse">
      <div className="w-12 h-12 sm:w-14 sm:h-14 rounded-full bg-gray-300 dark:bg-gray-600 shrink-0" />
      <div className="ml-4 flex flex-col flex-1 min-w-0">
        <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded w-3/4 mb-2" />
        <div className="flex items-center gap-2 mt-1">
          <div className="h-3 bg-gray-300 dark:bg-gray-600 rounded w-1/4" />
          <div className="h-4 bg-gray-300 dark:bg-gray-600 rounded-full w-16" />
          <div className="h-3 bg-gray-300 dark:bg-gray-600 rounded w-10 ml-auto" />
        </div>
      </div>
    </div>
  );
};
