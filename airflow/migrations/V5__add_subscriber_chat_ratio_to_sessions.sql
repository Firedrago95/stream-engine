-- ============================================================================
-- V5: stream_sessions에 구독자 채팅 비율(subscriber_chat_ratio) 컬럼 추가
-- ============================================================================

ALTER TABLE stream_sessions
    ADD COLUMN IF NOT EXISTS subscriber_chat_ratio DOUBLE PRECISION;
