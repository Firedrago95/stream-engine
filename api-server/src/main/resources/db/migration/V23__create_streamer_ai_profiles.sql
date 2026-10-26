-- ============================================================================
-- V23: 스트리머 AI 분석 프로필 테이블 생성
-- ============================================================================

CREATE TABLE streamer_ai_profiles (
    stream_id               VARCHAR(64)   PRIMARY KEY,
    summary_line            VARCHAR(255)  NOT NULL,
    broadcasting_style      TEXT          NOT NULL,
    core_games              JSONB         NOT NULL DEFAULT '[]'::jsonb,
    fandom_traits           TEXT          NOT NULL,
    popular_memes           JSONB         NOT NULL DEFAULT '[]'::jsonb,
    analyzed_session_count  INT           NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_streamer_ai_profiles_updated
    ON streamer_ai_profiles (updated_at DESC);
