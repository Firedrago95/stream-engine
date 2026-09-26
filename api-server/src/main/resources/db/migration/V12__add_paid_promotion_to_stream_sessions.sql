-- 방송 세션에 유료 프로모션(광고/숙제) 여부 컬럼 추가
ALTER TABLE stream_sessions ADD COLUMN IF NOT EXISTS paid_promotion BOOLEAN NOT NULL DEFAULT FALSE;

-- 유료 프로모션 세션만 최신순으로 빠르게 조회하기 위한 부분 인덱스 (Partial Index)
CREATE INDEX IF NOT EXISTS idx_stream_sessions_paid_promotion
    ON stream_sessions (stream_id, started_at DESC)
    WHERE paid_promotion = TRUE;
