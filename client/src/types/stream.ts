export interface StreamItem {
  streamId: string;
  streamerName: string;
  liveTitle: string;
  thumbnailUrl: string;
  categoryName: string;
  status: 'ANALYZING' | 'LIVE';
}
