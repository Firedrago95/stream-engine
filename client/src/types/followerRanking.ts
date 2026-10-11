import { z } from 'zod';

export const FollowerRankingItemSchema = z.object({
  streamId: z.string(),
  streamerName: z.string(),
  liveTitle: z.string().nullable().optional(),
  profileImageUrl: z.string().nullable().optional(),
  categoryName: z.string().nullable().optional(),
  status: z.enum(['ANALYZING', 'LIVE', 'OFFLINE']).catch('OFFLINE'),
  concurrentUserCount: z.number().optional().catch(0),
  followerCount: z.number().optional().catch(0),
  weeklyGrowth: z.number().optional().catch(0),
});

export type FollowerRankingItem = z.infer<typeof FollowerRankingItemSchema>;
export type FollowerRankingType = 'GROWTH' | 'TOTAL';
