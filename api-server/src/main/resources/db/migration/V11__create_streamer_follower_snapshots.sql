-- 1. streams 테이블에 마지막 팔로워 수집 시각 컬럼 추가
ALTER TABLE streams ADD COLUMN IF NOT EXISTS last_follower_updated_at TIMESTAMPTZ;

-- 2. 스트리머 일별 팔로워 스냅샷 히스토리 테이블 생성
CREATE TABLE streamer_follower_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    stream_id       VARCHAR(255) NOT NULL,
    snapshot_date   DATE NOT NULL,
    follower_count  INT NOT NULL,
    follower_growth INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_follower_snapshots_stream_date UNIQUE (stream_id, snapshot_date)
);

CREATE INDEX idx_follower_snapshots_stream_date_desc 
ON streamer_follower_snapshots (stream_id, snapshot_date DESC);
