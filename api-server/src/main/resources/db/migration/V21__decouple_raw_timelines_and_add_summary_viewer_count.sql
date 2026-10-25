ALTER TABLE stream_sessions
    ADD COLUMN IF NOT EXISTS viewer_sample_count INT NOT NULL DEFAULT 0;

ALTER TABLE analysis_signals_summary
    ADD COLUMN IF NOT EXISTS viewer_count INT NOT NULL DEFAULT 0;
