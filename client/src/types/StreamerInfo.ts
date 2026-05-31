export interface StreamerInfo {
    streamId: string;
    streamerName: string;
    profileImageUrl: string | null;
    liveTitle: string | null;
    categoryName: string | null;
    concurrentUserCount: number;
    status: 'ANALYZING' | 'LIVE' | 'OFFLINE';
}