-- 방송 세그먼트에 유료 프로모션(광고/숙제) 여부 컬럼 추가
ALTER TABLE stream_session_segments ADD COLUMN IF NOT EXISTS paid_promotion BOOLEAN NOT NULL DEFAULT FALSE;
