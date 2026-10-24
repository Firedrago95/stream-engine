-- ============================================================================
-- 치즈픽 데이터 웨어하우스 (cheesepick_dw) 활성 세션/세그먼트 부분 인덱스 추가 (V2)
-- ============================================================================

CREATE INDEX idx_dw_sessions_active
    ON stream_sessions (stream_id, session_id)
    WHERE ended_at IS NULL;

CREATE UNIQUE INDEX idx_dw_segments_active
    ON stream_session_segments (session_id)
    WHERE ended_at IS NULL;
