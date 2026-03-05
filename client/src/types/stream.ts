import { z } from 'zod';

export const StreamItemSchema = z.object({
  streamId: z.string(),
  streamerName: z.string(),
  liveTitle: z.string().nullable().optional(),
  profileImageUrl: z.string().nullable().optional(),
  categoryName: z.string().nullable().optional(),
  status: z.enum(['ANALYZING', 'LIVE', 'OFFLINE']).catch('LIVE'),
});

export type StreamItem = z.infer<typeof StreamItemSchema>;
