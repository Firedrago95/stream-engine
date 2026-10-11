-- 1. 누적 팔로워 순위 조회를 위한 streams 테이블 follower_count 인덱스
CREATE INDEX IF NOT EXISTS idx_streams_follower_count
ON streams (follower_count DESC NULLS LAST);

-- 2. 주간 팔로워 급상승 집계를 위한 streamer_follower_snapshots snapshot_date 인덱스
CREATE INDEX IF NOT EXISTS idx_follower_snapshots_date
ON streamer_follower_snapshots (snapshot_date DESC);
