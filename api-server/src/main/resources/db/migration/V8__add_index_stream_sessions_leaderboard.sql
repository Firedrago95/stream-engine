CREATE INDEX IF NOT EXISTS idx_stream_sessions_leaderboard_calc
    ON stream_sessions (started_at DESC, stream_id)
    INCLUDE (average_viewer_count)
    WHERE average_viewer_count > 0;

