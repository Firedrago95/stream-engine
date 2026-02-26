export const StreamSkeleton = () => (
    <div className="flex flex-col gap-3 animate-pulse">
      <div className="w-full aspect-video bg-white/5 rounded-xl" />
      <div className="h-4 w-3/4 bg-white/5 rounded" />
      <div className="h-3 w-1/2 bg-white/5 rounded" />
    </div>
);
