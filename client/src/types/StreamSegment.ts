export interface StreamSegment {
    id: number;
    title: string;
    categoryName: string;
    startedAt: string;
    endedAt: string | null;
    startOffsetMs: number;
    endOffsetMs: number | null;
}