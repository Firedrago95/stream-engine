CREATE TABLE streamer_daily_stats (
    id                         BIGSERIAL PRIMARY KEY,
    channel_id                 VARCHAR(64) NOT NULL,
    stat_date                  DATE NOT NULL,
    broadcast_duration_seconds BIGINT NOT NULL DEFAULT 0,
    average_viewers            INT NOT NULL DEFAULT 0,
    peak_viewers               INT NOT NULL DEFAULT 0,
    hours_watched              DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    follower_count             INT,
    follower_growth            INT DEFAULT 0,
    representative_title       VARCHAR(255),
    dominant_category          VARCHAR(100),
    session_count              INT NOT NULL DEFAULT 1,
    created_at                 TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_streamer_daily_stats_channel_date UNIQUE (channel_id, stat_date)
);

CREATE INDEX idx_streamer_daily_stats_channel_date_desc ON streamer_daily_stats (channel_id, stat_date DESC);
