import { z } from 'zod';

export const StreamItemSchema = z.object({
  streamId: z.string(),
  streamerName: z.string(),
  liveTitle: z.string(),
  thumbnailUrl: z.string(),
  categoryName: z.string(),
  status: z.enum(['ANALYZING', 'LIVE']),
});

export type StreamItem = z.infer<typeof StreamItemSchema>;
