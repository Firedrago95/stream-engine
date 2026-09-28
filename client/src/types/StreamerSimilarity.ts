export type SimilarityStatus = 'NORMAL' | 'INSUFFICIENT_DATA' | 'INDEPENDENT_FANDOM';

export interface SimilarChannelItem {
  rank: number;
  channelId: string;
  streamerName: string;
  profileImageUrl: string | null;
  primaryCategory: string | null;
  similarityPercent: number;
  commonChatterCount: number | null;
  totalChatterCount: number | null;
}

export interface StreamerSimilarityResponse {
  streamId: string;
  status: SimilarityStatus;
  calculatedDate: string | null;
  items: SimilarChannelItem[];
}
