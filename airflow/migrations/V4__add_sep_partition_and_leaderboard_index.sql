-- ============================================================================
-- 치즈픽 데이터 웨어하우스 (cheesepick_dw) V4: 2026년 9월 파티션 및 리더보드 커버링 인덱스 추가
-- ============================================================================

-- 1. OCI 과거 데이터(2026-09-10 ~ 2026-09-30) 백필 이관을 위한 2026년 9월 시계열 파티션 추가
CREATE TABLE IF NOT EXISTS view_metric_timelines_y2026m09 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2026-09-01 00:00:00+09') TO ('2026-10-01 00:00:00+09');

CREATE TABLE IF NOT EXISTS chat_signal_10s_y2026m09 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2026-09-01 00:00:00+09') TO ('2026-10-01 00:00:00+09');

-- 2. 스트리머 30일 리더보드 및 타겟 300명 산출(Index-Only Scan)을 위한 커버링 부분 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_dw_sessions_leaderboard_calc
    ON stream_sessions (started_at DESC, stream_id)
    INCLUDE (average_viewer_count, ended_at)
    WHERE average_viewer_count > 0;
