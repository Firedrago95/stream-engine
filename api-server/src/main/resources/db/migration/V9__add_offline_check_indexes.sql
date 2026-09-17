-- 종료 감지 시 방송 중인 활성 세션만 초고속 조회하기 위한 부분 인덱스 (Partial Index)
CREATE INDEX IF NOT EXISTS idx_stream_sessions_active
    ON stream_sessions (stream_id)
    WHERE ended_at IS NULL;

-- 오프라인 판정 시 미갱신 라이브 방송을 빠르게 조회/갱신하기 위한 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_streams_offline_check
    ON streams (is_live, last_update_at);

-- 일별 잔디 스냅샷(새벽 4시) 적재 시 활성 세션의 시작 시각 필터/정렬을 보조하는 부분 인덱스
CREATE INDEX IF NOT EXISTS idx_stream_sessions_active_started_at
    ON stream_sessions (started_at)
    WHERE ended_at IS NULL;
