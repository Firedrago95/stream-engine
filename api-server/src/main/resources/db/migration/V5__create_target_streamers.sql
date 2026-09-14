CREATE TABLE target_streamers (
    id             BIGSERIAL PRIMARY KEY,
    channel_id     VARCHAR(64) NOT NULL UNIQUE,
    streamer_name  VARCHAR(100) NOT NULL,
    target_type    VARCHAR(20) NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_target_streamers_active ON target_streamers (is_active, target_type);

INSERT INTO target_streamers (channel_id, streamer_name, target_type, is_active)
VALUES ('938b564e937346146607e153e7d6928e', '치지직 공식', 'STATIC', true)
ON CONFLICT (channel_id) DO NOTHING;
