-- ============================================================================
-- V22: highlight_events에 v2.1 사후 분석 알고리즘 지표 (순위, Z-Score, LLR, 태그) 추가
-- ============================================================================

ALTER TABLE highlight_events
    ADD COLUMN IF NOT EXISTS rank_in_session INT,
    ADD COLUMN IF NOT EXISTS peak_z DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS llr_score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS top_tags JSONB NOT NULL DEFAULT '{"words": [], "emotes": []}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_highlight_events_session_rank
    ON highlight_events (session_id, rank_in_session);

CREATE INDEX IF NOT EXISTS idx_stream_sessions_recent_finished
    ON stream_sessions (ended_at DESC NULLS LAST);
