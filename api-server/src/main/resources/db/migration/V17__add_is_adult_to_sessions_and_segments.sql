-- 방송 세션 및 세그먼트에 연령 제한(19금) 여부 컬럼 추가
ALTER TABLE stream_sessions ADD COLUMN IF NOT EXISTS is_adult BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stream_session_segments ADD COLUMN IF NOT EXISTS is_adult BOOLEAN NOT NULL DEFAULT FALSE;
