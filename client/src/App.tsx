import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { useStreams } from './hooks/useStreams';
import { StreamCard } from './components/stream/StreamCard';
import { StreamSkeleton } from './components/common/Skeleton';
import { StreamAnalysisDashboard } from './components/stream/StreamAnalysisDashboard';
import { ErrorBoundary } from './components/common/ErrorBoundary';

const StreamList = () => {
  const { streams, isLoading, error } = useStreams(15000);

  return (
      <div className="max-w-[1800px] mx-auto p-6 md:p-10">
        <header className="flex items-center justify-between mb-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-chzzk-green rounded-lg flex items-center justify-center shadow-neon">
              <span className="text-black font-black text-xl">S</span>
            </div>
            <h1 className="text-2xl font-black tracking-tighter">SLICE <span className="text-chzzk-green">STREAM</span></h1>
          </div>
          <div className="hidden sm:flex items-center gap-3 px-4 py-2 bg-white/5 rounded-full border border-white/10">
            <div className="w-2 h-2 rounded-full bg-chzzk-green animate-pulse" />
            <span className="text-xs font-bold text-gray-400 tracking-widest uppercase">System Operational</span>
          </div>
        </header>

        {error ? (
            <div className="h-64 flex items-center justify-center text-red-400 border border-red-400/20 rounded-2xl bg-red-400/5">
              {error}
            </div>
        ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6 gap-x-5 gap-y-10">
              {isLoading
                  ? [...Array(10)].map((_, i) => <StreamSkeleton key={i} />)
                  : streams.map(s => <StreamCard key={s.streamId} stream={s} />)
              }
            </div>
        )}
      </div>
  );
};

function App() {
  return (
      <ErrorBoundary>
        <BrowserRouter>
          <div className="min-h-screen bg-chzzk-dark text-white selection:bg-chzzk-green selection:text-black">
            <Routes>
              <Route path="/" element={<StreamList />} />
              <Route
                  path="/streams/:streamId"
                  element={
                    <ErrorBoundary fallback={<div className="p-10 text-center text-red-400">대시보드를 불러오는 중 치명적인 오류가 발생했습니다.</div>}>
                      <StreamAnalysisDashboard />
                    </ErrorBoundary>
                  }
              />
            </Routes>
          </div>
        </BrowserRouter>
      </ErrorBoundary>
  );
}

export default App;
