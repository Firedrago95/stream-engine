ALTER TABLE streams ADD COLUMN follower_count INT;
ALTER TABLE streams ADD COLUMN subscriber_count INT;
ALTER TABLE stream_sessions ADD COLUMN average_viewer_count INT;
ALTER TABLE stream_sessions ADD COLUMN session_follower_growth INT;
ALTER TABLE stream_sessions ADD COLUMN subscriber_chat_ratio DOUBLE PRECISION;

CREATE TABLE view_metric_timelines (
    id            BIGSERIAL PRIMARY KEY,
    stream_id     VARCHAR(255) NOT NULL,
    session_id    VARCHAR(255) NOT NULL,
    timestamp     TIMESTAMPTZ  NOT NULL,
    viewer_count  INT NOT NULL
);
-- 특정 방송의 타임라인 메트릭 정보를 조회하기 위한 용도
CREATE INDEX idx_viewer_metric_session ON view_metric_timelines (session_id, timestamp DESC);
