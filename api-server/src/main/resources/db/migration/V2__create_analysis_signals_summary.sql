CREATE TABLE analysis_signals_summary
(
    id               BIGSERIAL PRIMARY KEY,
    stream_id        VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    firepower_avg    BIGINT       NOT NULL,
    firepower_max    BIGINT       NOT NULL,
    timestamp_minute TIMESTAMPTZ  NOT NULL,
    UNIQUE (stream_id, status, timestamp_minute)
);

CREATE INDEX idx_summary_stream_time ON analysis_signals_summary (stream_id, timestamp_minute DESC);
