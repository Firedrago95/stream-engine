CREATE INDEX IF NOT EXISTS idx_streams_concurrent_user_count_desc
    ON streams (concurrent_user_count DESC);
