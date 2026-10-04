DROP INDEX IF EXISTS idx_stream_sessions_leaderboard_calc;

CREATE INDEX idx_stream_sessions_leaderboard_calc
    ON stream_sessions (started_at DESC, stream_id)
    INCLUDE (average_viewer_count, ended_at)
    WHERE average_viewer_count > 0;
